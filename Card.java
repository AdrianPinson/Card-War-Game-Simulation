package war;

public class Card
{
	char suit;
	String face;
	int value;
	
	Card next;
	
	public Card(char s, String f, int v)
	{
		suit = s;
		face = f;
		value = v;
		
		next = null;
	}
	
	@Override
	public String toString()
	{
		return String.format("%s%c", face, suit);
	}

}
