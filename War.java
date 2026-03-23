package war;
import java.util.Scanner;
public class War
{
	public static final int NUMCARDS = 52;
	public static void main(String[] args)
	{
		Scanner scan = new Scanner(System.in);
		Card[] deck = new Card[NUMCARDS];
		makeDeck(deck);
		//printDeck(deck);
		shuffle(deck);
		//printDeck(deck);
		//get # of players
		System.out.print("Welcome to Card War\n\nHow many Players? ");
		int playerCount = Integer.parseInt(scan.nextLine());
		Player[] player = new Player[playerCount];
		for(int x = 0; x < playerCount; x++)
		{
			player[x] = new Player(scan);
		}
		deal(deck,player);
		//PLay the game
		while(playing(player))
		{
			playHand(player);
			checkDecks(player);
		}
		System.out.println("Game Over");
		scan.close();
	}
	private static void checkDecks(Player[] player)
	{
		for(int x = 0; x< player.length; x++)
		{
			player[x].flip();
		}
	}
	
	private static void playHand(Player[] player) {

	    Card[] tableCards = new Card[player.length];
	    boolean[] active = new boolean[player.length];

	    System.out.println();
	    for(int i = 0; i < player.length; i++) {
	        System.out.print(" " + player[i].name + " ");
	    }
	    System.out.println();

	    printTops(player);

	    int maxValue = -1;

	    // Each player plays one card
	    for(int i = 0; i < player.length; i++) {

	        if(player[i].playing) {

	            Card c = player[i].drawCard();
	            tableCards[i] = c;

	            if(c == null) {
	                active[i] = false;
	                System.out.print(" -- ");
	                continue;
	            }

	            active[i] = true;
	            System.out.print(" \u2502" + c + "\u2502");

	            if(c.value > maxValue)
	                maxValue = c.value;
	        }
	        else {
	            System.out.print(" -- ");
	        }
	    }

	    System.out.println();
	    printBottoms(player);

	    // Detect ties
	    int tieCount = 0;
	    for(int i=0;i<player.length;i++)
	        if(active[i] && tableCards[i].value == maxValue)
	            tieCount++;

	    // If tie → BATTLE
	    if(tieCount > 1) {
	        resolveBattle(player, tableCards, active);
	    }
	    else {
	        // Normal win
	        for(int i=0;i<player.length;i++) {
	            if(active[i] && tableCards[i].value == maxValue) {
	                player[i].wins(tableCards);
	                break;
	            }
	        }
	    }
	}
	
	private static void printBottoms(Player[] player)
	{
		for(int x = 0; x < player.length; x++ )
		{
			if (player[x].playing)
			{
				System.out.print(" \u2558\u2550\u2550\u255B ");
			}
			else
			{
				System.out.print(" ");
			}
		}
		System.out.println();
	}
	private static void printTops(Player[] player)
	{
		for(int x = 0; x < player.length; x++ )
		{
			if (player[x].playing)
			{
				System.out.print(" \u2552\u2550\u2550\u2555 ");
			}
			else
			{
				System.out.print(" ");
			}
		}
		System.out.println();
	}
	private static boolean playing(Player[] player)
	{
		//at least two players still have cards
		int count = 0;
		for(int x = 0; x < player.length; x++)
		{
			if(player[x].playing)
			{
				count++;
			}
		}
		if (count > 1)
		{
			return true;
		}
		for(int x = 0; x < player.length; x++)
		{
			if(player[x].playing)
			{
				System.out.println(player[x].name
						+ " is the winner");
			}
		}
		return false;
	}
	private static void deal(Card[] deck, Player[] player)
	{
		//Deal to stacks
		for(int y = 0; y < NUMCARDS; y++)
		{
			player[y%player.length].play.push(deck[y]);
		}
	}
	private static void printDeck(Card[] deck)
	{
		System.out.println("\nCurrent Deck:");
		for(int x = 0; x < deck.length; x++)
		{
			System.out.println(deck[x]);
		}
	}
	private static void shuffle(Card[] deck)
	{
		for(int x = 0; x < deck.length; x++)
		{
			int swap = (int)(Math.random() * NUMCARDS);
			Card temp = deck[x];
			deck[x]=deck[swap];
			deck[swap]=temp;
		}
	}
	private static void makeDeck(Card[] deck)
	{
		int cardCount = 0;
		for(int i = 0; i < 4; i++) //suits
		{
			char suit = ' ';
			switch(i)
			{
			case 0:
				suit = '\u2660';
				break;
			case 1:
				suit = '\u2663';
				break;
			case 2:
				suit = '\u2665';
				break;
			case 3:
				suit = '\u2666';
				break;
			}
			for(int value = 2; value < 15; value++ ) //faces/values
			{
				String face = "" + value;
				if (value == 11)
				{
					face = "J";
				}
				else if (value == 12)
				{
					face = "Q";
				}
				else if (value == 13)
				{
					face = "K";
				}
				else if (value == 14)
				{
					face = "A";
				}
				Card c = new Card(suit,face,value);
				deck[cardCount++] = c;
			}
		}
	}
	
	private static void resolveBattle(Player[] player, Card[] pile, boolean[] battling)
	{
		System.out.println("\n***** BATTLE! *****\n");
		
		while(true)
		{
			//burn 3 cards
			for(int b = 0; b < 3; b++)
			{
				for(int i = 0; i < player.length; i++)
				{
					if(!battling[i]) continue;
					
					Card burn = player[i].drawCard();
					
					if(burn == null)
					{
						battling[i] = false;
					}
					else
					{
						pile = addToPile(pile, burn);
					}
					
				}
			}
			
			//flip battle card
			int maxValue = -1;
			Card[] battleFlip = new Card[player.length];
			
			for(int i = 0; i < player.length; i++)
			{
				if(!battling[i]) continue;
				
				Card c = player[i].drawCard();
				
				if(c == null)
				{
					battling[i] = false;
					continue;
				}
				
				battleFlip[i] = c;
				pile = addToPile(pile, c);
				
				System.out.println(player[i].name + "flips " + c);
				
				if(c.value > maxValue)
					maxValue = c.value;
			}
			
			//check remaining tied players
			int tieCount = 0;
			int winnerIndex = -1;
			
			for(int i = 0; i < player.length; i++)
			{
				if(battleFlip[i] != null && battleFlip[i].value == maxValue)
				{
					tieCount++;
					winnerIndex = i;
				}
			}
			
			// single winner
			if(tieCount == 1)
			{
				player[winnerIndex].wins(pile);
				return;
			}
			
			//otherwise continue battle
			System.out.println("\n--- Battle continues! ---\n");
			
			for(int i = 0; i < player.length; i++)
			{
				battling[i] = (battleFlip[i] != null &&
						battleFlip[i].value == maxValue);
			}
		}
	}
	private static Card[] addToPile(Card[] pile, Card c)
	{
		Card[] newPile = new Card[pile.length + 1];
		
		for(int i = 0; i < pile.length; i++)
		{
			newPile [i] = pile[i];
		}
		newPile[pile.length] = c;
		
		return newPile;
	}
}

