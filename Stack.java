package war;

public class Stack
{
	private Card head;
	//constructor
	public Stack()
	{
		head = null;
	}
	public void push(Card c)
	{
		c.next = head;
		head = c;
	}
	public Card pop()
	{
		Card c = head;
		head = c.next;
		return c;
	}
	public boolean isEmpty()
	{
		if(head == null)
		{
			return true;
		}
		return false;
	}
}
