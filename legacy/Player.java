package war;
import java.util.Scanner;
public class Player
{
	String name;
	Stack play;
	Stack win;
	boolean playing;
	static int count = 0;
	public Player(Scanner scan)
	{
		System.out.print("Enter Player " + ++count + " name: ");
		name = scan.nextLine();
		play = new Stack();
		win = new Stack();
		playing = true;
	}
	private boolean stillAlive()
	{
		if(play.isEmpty() && win.isEmpty())
		{
			if (playing == true)
			{
				System.out.println(name + " is out of the game.");
			}
			playing = false;
			return false;
		}
		return true;
	}
	public void wins(Card[] playCards)
	{
		for(int x = 0; x < playCards.length; x++)
		{
			Card c = playCards[x];
			if (c != null)
			{
				win.push(c);
			}
		}
		System.out.println(name + " wins\n");
	}
	public void flip()
	{
		if(stillAlive())
		{
			if(play.isEmpty())
			{
				while(!win.isEmpty())
				{
					play.push(win.pop());
				}
				System.out.println(name + " win stack flipped\n\n");
			}
		}
	}
	public Card drawCard() 
	{
		flip();
		
		if(play.isEmpty())
		{
			playing = false;
			System.out.println(name + "is out of the game.");
			return null;
		}
		return play.pop();
	}
}
