import java.util.Collections;
import java.util.Stack;

/**
 * FlushPlayer - a simple example implementation of the player interface for
 * PokerSquares that attempts to get flushes in the first four columns. Author:
 * Yuhan Ren, based on code provided by Todd W. Neller and Michael Fleming
 */
public class FlushPlayer implements PokerSquaresPlayer {

	private final int SIZE = 5; // number of rows/columns in square grid
	private final int NUM_POS = SIZE * SIZE; // number of positions in square grid
	private final int NUM_CARDS = Card.NUM_CARDS; // number of cards in deck
	private Card[][] grid = new Card[SIZE][SIZE]; // grid with Card objects or null (for empty positions)

	/*
	 * (non-Javadoc)
	 * 
	 * @see PokerSquaresPlayer#setPointSystem(PokerSquaresPointSystem, long)
	 */
	@Override
	public void setPointSystem(PokerSquaresPointSystem system, long millis) {
		// The FlushPlayer, like the RandomPlayer, does not worry about the scoring
		// system.
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see PokerSquaresPlayer#init()
	 */
	@Override
	public void init() {
		// clear grid
		for (int row = 0; row < SIZE; row++)
			for (int col = 0; col < SIZE; col++)
				grid[row][col] = null;

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see PokerSquaresPlayer#getPlay(Card, long)
	 */
	@Override
	public int[] getPlay(Card card, long millisRemaining) {
		int cardrow = 0;
		int cardcol = 0;
		boolean placed = false;

		int cardsuit = card.getSuit();

		for (int i = 0; i <= 4; i++) {
			// check whether can put it in appropriate col
			if (grid[i][cardsuit] == null) {
				cardrow = i;
				cardcol = cardsuit;
				placed = true;
				break;
			}

		}
		if (!placed) {
			for (int i = 0; i <= 4; i++) {
				// check whether can put it in col 4
				if (grid[i][4] == null) {
					cardrow = i;
					cardcol = 4;
					placed = true;
					break;
				}
			}
		}
		if (!placed) {
			for (int i = 0; i <= 4; i++) {
				for (int j = 0; j <= 3; j++) {
					// check whether can put it in the topmost available square
					if (grid[i][j] == null) {
						cardrow = i;
						cardcol = j;
						placed = true;
						break;
					}
				}
			}
		}

		grid[cardrow][cardcol] = card;

		int[] playPos = { cardrow, cardcol };
		return playPos;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see PokerSquaresPlayer#getName()
	 */
	@Override
	public String getName() {
		return "FlushPlayer";
	}

	/**
	 * Demonstrate FlushPlayer play with British point system.
	 * 
	 * @param args (not used)
	 */
	public static void main(String[] args) {
		PokerSquaresPointSystem system = PokerSquaresPointSystem.getBritishPointSystem();
		System.out.println(system);
		new PokerSquares(new FlushPlayer(), system).play(); // play a single game
	}

}
