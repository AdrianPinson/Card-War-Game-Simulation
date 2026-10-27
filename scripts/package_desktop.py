#!/usr/bin/env python3
"""Package and verify OS/CPU-specific portable and bundled-runtime distributions."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / 'target'
MAIN = 'war.ui.DesktopLauncher'


def run(command, **kwargs):
    print('+', ' '.join(map(str, command)), flush=True)
    argv = list(map(str, command))
    if os.name == 'nt' and argv[:3] == ['cmd.exe', '/d', '/c']:
        # cmd strips one outer quote pair. Keep the quoted script path and
        # quoted report path together inside an additional outer pair.
        argv = 'cmd.exe /d /s /c "' + subprocess.list2cmdline(argv[3:]) + '"'
    try:
        return subprocess.run(argv, check=True, **kwargs)
    except subprocess.CalledProcessError as error:
        for output in [error.stdout, error.stderr]:
            if output:
                print(output.decode('utf-8', errors='replace') if isinstance(output, bytes) else output, file=sys.stderr)
        raise


def tool(name):
    executable = name + ('.exe' if os.name == 'nt' else '')
    configured = os.environ.get('JAVA_HOME')
    candidate = Path(configured) / 'bin' / executable if configured else None
    if candidate and candidate.is_file():
        return candidate
    found = shutil.which(executable)
    if not found:
        raise RuntimeError(f'{name} is missing; install a full JDK 17+ and set JAVA_HOME')
    return Path(found)


def version():
    text = ET.parse(ROOT / 'pom.xml').getroot().find('{http://maven.apache.org/POM/4.0.0}version').text
    if not re.fullmatch(r'\d+\.\d+\.\d+', text):
        raise ValueError('Release version must be x.y.z')
    return text


def platform_name():
    system = {'Linux': 'linux', 'Windows': 'windows', 'Darwin': 'macos'}[platform.system()]
    arch = {'x86_64': 'x64', 'amd64': 'x64', 'arm64': 'arm64', 'aarch64': 'arm64'}[platform.machine().lower()]
    return f'{system}-{arch}'


def digest(path):
    with path.open('rb') as source:
        return hashlib.file_digest(source, 'sha256').hexdigest()


def remove(path):
    for attempt in range(20):
        try:
            if path.exists():
                shutil.rmtree(path)
            return
        except PermissionError:
            if attempt == 19:
                raise
            time.sleep(.5)


def manifest(directory, bundled):
    revision = os.environ.get('GITHUB_SHA', '')
    if not re.fullmatch('[0-9a-f]{40}', revision):
        revision = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    dirty = bool(subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT, text=True).strip())
    (directory / 'PROVENANCE.json').write_text(json.dumps({
        'project': 'War', 'version': version(), 'platform': platform_name(),
        'sourceRepository': 'https://github.com/AdrianPinson/Card-War-Game-Simulation',
        'sourceCommit': revision, 'workingTreeDirty': dirty, 'bundledRuntime': bundled,
        'requiresJava': None if bundled else '17+',
        'buildJava': subprocess.check_output([tool('java'), '-version'], stderr=subprocess.STDOUT, text=True).strip(),
        'javafx': '21.0.8',
    }, indent=2) + '\n', encoding='utf-8')
    paths = sorted(p for p in directory.rglob('*') if p.is_file() and p.name != 'SHA256SUMS.txt')
    (directory / 'SHA256SUMS.txt').write_text(''.join(f'{digest(p)}  {p.relative_to(directory).as_posix()}\n' for p in paths), encoding='utf-8')


def zip_tree(source, archive):
    pending = archive.with_suffix('.zip.tmp')
    try:
        with zipfile.ZipFile(pending, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as output:
            for path in sorted(source.rglob('*')):
                name = path.relative_to(source.parent).as_posix()
                if path.is_symlink():
                    info = zipfile.ZipInfo(name)
                    info.create_system = 3
                    info.external_attr = (stat.S_IFLNK | 0o777) << 16
                    output.writestr(info, os.readlink(path))
                elif path.is_file():
                    output.write(path, name)
        with zipfile.ZipFile(pending) as check:
            if check.testzip() is not None:
                raise ValueError('Generated ZIP failed integrity verification')
        with pending.open('r+b') as completed:
            os.fsync(completed.fileno())
        os.replace(pending, archive)
    finally:
        pending.unlink(missing_ok=True)


def unpack(archive, directory):
    directory.mkdir(parents=True)
    with zipfile.ZipFile(archive) as source:
        for item in source.infolist():
            path = directory / item.filename
            if not path.resolve().is_relative_to(directory.resolve()):
                raise ValueError('Invalid archive path')
            mode = item.external_attr >> 16
            path.parent.mkdir(parents=True, exist_ok=True)
            if item.is_dir():
                path.mkdir(exist_ok=True)
            elif stat.S_ISLNK(mode):
                target = source.read(item).decode()
                if not (path.parent / target).resolve().is_relative_to(directory.resolve()):
                    raise ValueError('Invalid archive link')
                path.symlink_to(target)
            else:
                with source.open(item) as content, path.open('wb') as output:
                    shutil.copyfileobj(content, output)
                if mode and os.name != 'nt':
                    path.chmod(mode & 0o777)


def check_manifest(directory):
    for line in (directory / 'SHA256SUMS.txt').read_text(encoding='utf-8').splitlines():
        expected, name = line.split('  ', 1)
        if digest(directory / name) != expected:
            raise ValueError('Extracted checksum mismatch: ' + name)
    metadata = json.loads((directory / 'PROVENANCE.json').read_text(encoding='utf-8'))
    if metadata['platform'] != platform_name() or metadata['version'] != version():
        raise ValueError('Provenance mismatch')


def native_launcher(image):
    if os.name == 'nt':
        return image / 'WarCardTable.exe'
    if platform.system() == 'Darwin':
        return image / 'Contents/MacOS/WarCardTable'
    return image / 'bin/WarCardTable'


def probe(command, report, cwd, bundled=None):
    report.unlink(missing_ok=True)
    env = os.environ.copy()
    if bundled:
        env['JAVA_HOME'] = str(cwd / 'no-external-java-here')
    run(command + ['--smoke-test', '--report', report], cwd=cwd, env=env, timeout=45, capture_output=True)
    text = report.read_text(encoding='utf-8')
    if not text.startswith('War packaged runtime: OK\n'):
        raise ValueError('Missing package probe report')
    java_home = Path(next(line.split('=', 1)[1] for line in text.splitlines() if line.startswith('java.home=')))
    if bundled and not java_home.resolve().is_relative_to(bundled.resolve()):
        raise ValueError('Bundled launcher used external Java')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--skip-build', action='store_true')
    parser.add_argument('--portable-only', action='store_true', help='Skip jlink/jpackage; Java 17+ is required to run the portable ZIP')
    parser.add_argument('--verify-gui', action='store_true', help='Also exercise the extracted native GUI; requires a display')
    args = parser.parse_args()
    label = platform_name()
    if not args.skip_build:
        maven = ['cmd.exe', '/d', '/c', 'mvn'] if os.name == 'nt' else [shutil.which('mvn') or 'mvn']
        run(maven + ['-B', '-Pdesktop-package', 'clean', 'verify'], cwd=ROOT, timeout=300)
    jar = TARGET / 'war-simulation.jar'
    with zipfile.ZipFile(jar) as check:
        if check.testzip() or not {'war/War.class', 'war/ui/DesktopLauncher.class', 'war/ui/table.css'}.issubset(check.namelist()):
            raise ValueError('Missing application classes/resources')
    libs = TARGET / 'desktop-libs'
    classifier = {'Linux': 'linux', 'Windows': 'win', 'Darwin': 'mac'}[platform.system()]
    if label.endswith('arm64'):
        classifier += '-aarch64'
    for module in ['base', 'graphics', 'controls']:
        if not (libs / f'javafx-{module}-21.0.8-{classifier}.jar').is_file():
            raise ValueError('Missing JavaFX library for ' + label + '; build with -Pdesktop-package on this platform')
    stage = TARGET / 'package-stage'
    remove(stage); stage.mkdir()
    app = stage / f'war-{version()}-{label}-portable'; app.mkdir()
    shutil.copy2(jar, app / jar.name); shutil.copytree(libs, app / 'lib')
    for file in ['README.txt', 'start-war.sh', 'start-war.cmd']:
        shutil.copy2(ROOT / 'packaging' / file, app / file)
    (app / 'start-war.sh').chmod(0o755)
    shutil.copytree(ROOT / 'docs', app / 'docs')
    shutil.copy2(ROOT / 'THIRD_PARTY.md', app / 'THIRD_PARTY.md')
    shutil.copytree(ROOT / 'licenses', app / 'licenses')
    manifest(app, False)
    releases = TARGET / 'releases'; releases.mkdir(exist_ok=True)
    portable = releases / f'{app.name}.zip'; zip_tree(app, portable)
    archives = [portable]
    image = None
    if not args.portable_only:
        runtime, native = TARGET / 'runtime', TARGET / 'native'
        remove(runtime); remove(native)
        run([tool('jlink'), '--add-modules', 'java.base,java.desktop,java.logging,java.xml,jdk.unsupported',
             '--strip-debug', '--no-header-files', '--no-man-pages', '--compress=2', '--output', runtime], cwd=ROOT, timeout=180)
        run([tool('jpackage'), '--type', 'app-image', '--name', 'WarCardTable', '--app-version', version(),
             '--vendor', 'Adrian Pinson', '--description', 'Seeded War card table and simulation lab',
             '--input', app, '--main-jar', jar.name, '--main-class', MAIN,
             '--runtime-image', runtime, '--dest', native], cwd=ROOT, timeout=180)
        image = native / ('WarCardTable.app' if platform.system() == 'Darwin' else 'WarCardTable')
        bundle = stage / f'war-{version()}-{label}-bundled'; bundle.mkdir()
        shutil.copytree(image, bundle / image.name, symlinks=True)
        shutil.copy2(ROOT / 'packaging/README.txt', bundle / 'README.txt')
        shutil.copy2(ROOT / 'THIRD_PARTY.md', bundle / 'THIRD_PARTY.md')
        shutil.copytree(ROOT / 'licenses', bundle / 'licenses')
        shutil.copytree(ROOT / 'docs', bundle / 'docs')
        manifest(bundle, True)
        bundled = releases / f'{bundle.name}.zip'; zip_tree(bundle, bundled); archives.append(bundled)
    probe_root = TARGET / 'extracted package checks with spaces'; remove(probe_root); probe_root.mkdir()
    working = probe_root / 'unrelated working directory'; working.mkdir()
    for archive in archives:
        unpack(archive, probe_root / archive.stem)
        extracted = probe_root / archive.stem / archive.stem
        check_manifest(extracted)
        if archive == portable:
            launcher = extracted / ('start-war.cmd' if os.name == 'nt' else 'start-war.sh')
            command = ['cmd.exe', '/d', '/c', launcher] if os.name == 'nt' else [launcher]
            probe(command, probe_root / 'portable-report.txt', working)
            result = run([tool('java'), '-jar', extracted / jar.name, 'play', '--seed', '42', '--max-rounds', '10'],
                         cwd=working, capture_output=True, text=True, timeout=30)
            if json.loads(result.stdout)['result']['rounds'] != 10:
                raise ValueError('Extracted console JAR produced unexpected result')
        else:
            extracted_image = extracted / image.name
            command = [native_launcher(extracted_image)]
            probe(command, probe_root / 'bundled-report.txt', working, extracted_image)
            if args.verify_gui:
                report = probe_root / 'gui-report.txt'
                run(command + ['--smoke-ui', '--report', report], cwd=working, timeout=45, capture_output=True)
                if report.read_text(encoding='utf-8') != 'War packaged GUI: OK\n':
                    raise ValueError('Extracted GUI check failed')
    (releases / f'SHA256SUMS-{label}.txt').write_text(''.join(f'{digest(p)}  {p.name}\n' for p in archives), encoding='utf-8')
    print('PASS: extracted package checksums, provenance, launchers, replay/history/batch health checks, console, and bundled Java selection (when built)')


if __name__ == '__main__':
    main()
