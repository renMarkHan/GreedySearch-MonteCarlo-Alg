import java.util.ArrayList;
import java.util.Random;

/**
 * OptimizedGreedy - a combination of Greedy search and Monte Carlo implementation of the player interface for PokerSquares.
 * Author: Todd W. Neller
 * Modifications by: Yuhan Ren, Lixuan Bao
 */
public class Yren2PokerSquaresPlayer implements PokerSquaresPlayer {
	
	private final int SIZE = 5; // number of rows/columns in square grid
	private final int NUM_POS = SIZE * SIZE; // number of positions in square grid
	private final int NUM_CARDS = Card.NUM_CARDS; // number of cards in deck
	private Random random = new Random(); // pseudorandom number generator for Monte Carlo simulation 
	private int[] plays = new int[NUM_POS]; // positions of plays so far (index 0 through numPlays - 1) recorded as integers using row-major indices.
	// row-major indices: play (r, c) is recorded as a single integer r * SIZE + c (See http://en.wikipedia.org/wiki/Row-major_order)
	// From plays index [numPlays] onward, we maintain a list of yet unplayed positions.
	private int numPlays = 0; // number of Cards played into the grid so far
	private PokerSquaresPointSystem system; // point system
	private int depthLimit = 20; // default depth limit for Random Monte Carlo (MC) play
	private Card[][] grid = new Card[SIZE][SIZE]; // grid with Card objects or null (for empty positions)
	private Card[] simDeck = Card.getAllCards(); // a list of all Cards. As we learn the index of cards in the play deck,
	                                             // we swap each dealt card to its correct index.  Thus, from index numPlays 
												 // onward, we maintain a list of undealt cards for MC simulation.
	private int[][] legalPlayLists = new int[NUM_POS][NUM_POS]; // stores legal play lists indexed by numPlays (depth)
	// (This avoids constant allocation/deallocation of such lists during the selections of MC simulations.)

	/**
	 * Create a Random Monte Carlo player that simulates random play to depth 25.
	 */
	public Yren2PokerSquaresPlayer() {
	}
	
	/**
	 * Create a Random Monte Carlo player that simulates random play to a given depth limit.
	 * @param depthLimit depth limit for random simulated play
	 */
	public Yren2PokerSquaresPlayer(int depthLimit) {
		this.depthLimit = depthLimit;
	}
	
	/* (non-Javadoc)
	 * @see PokerSquaresPlayer#init()
	 */
	@Override
	public void init() { 
		// clear grid
		for (int row = 0; row < SIZE; row++)
			for (int col = 0; col < SIZE; col++)
				grid[row][col] = null;
		// reset numPlays
		numPlays = 0;
		// (re)initialize list of play positions (row-major ordering)
		for (int i = 0; i < NUM_POS; i++)
			plays[i] = i;
	}

	/* (non-Javadoc)
	 * @see PokerSquaresPlayer#getPlay(Card, long)
	 */
	@Override
	public int[] getPlay(Card card, long millisRemaining) {
		/*
		 * With this algorithm, the player chooses the legal play that has the highest expected score outcome.
		 * This outcome is estimated as follows:
		 *   For each move, many simulated random plays to the set depthLimit are performed and the (sometimes
		 *     partially-filled) grid is scored.
		 *   For each play simulation, random undrawn cards are drawn in simulation and the player
		 *     picks a play position by using the greedy function which is getValue().
		 *   After many such plays, the average score per simulated play is computed.  The play with the highest 
		 *     average score is chosen (breaking ties randomly).   
		 */
		
		// match simDeck to actual play event; in this way, all indices forward from the card contain a list of 
		//   undealt Cards in some permutation.
		int cardIndex = numPlays;
		while (!card.equals(simDeck[cardIndex]))
			cardIndex++;
		simDeck[cardIndex] = simDeck[numPlays];
		simDeck[numPlays] = card;

		if (numPlays < 24) { // not the forced last play
			// compute average time per move evaluation
			int remainingPlays = NUM_POS - numPlays; // ignores triviality of last play to keep a conservative margin for game completion
			long millisPerPlay = millisRemaining / remainingPlays; // dividing time evenly with future getPlay() calls
			long millisPerMoveEval = millisPerPlay / remainingPlays; // dividing time evenly across moves now considered
			// copy the play positions (row-major indices) that are empty
			System.arraycopy(plays, numPlays, legalPlayLists[numPlays], 0, remainingPlays);
			double maxAverageScore = Double.NEGATIVE_INFINITY; // maximum average score found for moves so far
			ArrayList<Integer> bestPlays = new ArrayList<Integer>(); // all plays yielding the maximum average score 
			for (int i = 0; i < remainingPlays; i++) { // for each legal play position
				int play = legalPlayLists[numPlays][i];
				long startTime = System.currentTimeMillis();
				long endTime = startTime + millisPerMoveEval; // compute when MC simulations should end
				makePlay(card, play / SIZE, play % SIZE);  // play the card at the empty position
				int simCount = 0;
				int scoreTotal = 0;
				while (System.currentTimeMillis() < endTime) { // perform as many MC simulations as possible through the allotted time
					// Perform a Monte Carlo simulation of random play to the depth limit or game end, whichever comes first.
					scoreTotal += simPlay(depthLimit);  // accumulate MC simulation scores
					simCount++; // increment count of MC simulations
				}
				undoPlay(); // undo the play under evaluation
				// update (if necessary) the maximum average score and the list of best plays
				double averageScore = (double) scoreTotal / simCount;
				if (averageScore >= maxAverageScore) {
					if (averageScore > maxAverageScore)
						bestPlays.clear();
					bestPlays.add(play);
					maxAverageScore = averageScore;
				}
			}
			int bestPlay = bestPlays.get(random.nextInt(bestPlays.size())); // choose a best play (breaking ties randomly)
			// update our list of plays, recording the chosen play in its sequential position; all onward from numPlays are empty positions
			int bestPlayIndex = numPlays;
			while (plays[bestPlayIndex] != bestPlay)
				bestPlayIndex++;
			plays[bestPlayIndex] = plays[numPlays];
			plays[numPlays] = bestPlay;
		}

		int[] playPos = {plays[numPlays] / SIZE, plays[numPlays] % SIZE}; // decode it into row and column
		makePlay(card, playPos[0], playPos[1]); // make the chosen play (not undoing this time)
		return playPos; // return the chosen play
	}

	/**
	 * From the chosen play, perform simulated Card draws and random placement (depthLimit) iterations forward 
	 * and return the resulting grid score.
	 * @param depthLimit - how many simulated random plays to perform
	 * @return resulting grid score after random MC simulation to given depthLimit
	 */
	private int simPlay(int depthLimit) {
		if (depthLimit == 0) { // with zero depth limit, return current score
			return system.getScore(grid);
		}
		else { // up to the non-zero depth limit or to game end, iteratively make the given number of random plays 
			int score = Integer.MIN_VALUE;
			int maxScore = Integer.MIN_VALUE;
			int depth = Math.min(depthLimit, NUM_POS - numPlays); // compute real depth limit, taking into account game end
			for (int d = 0; d < depth; d++) {
				// generate a random card draw
				int c = random.nextInt(NUM_CARDS - numPlays) + numPlays;
				Card card = simDeck[c];
				// choose a random play from the legal plays

				int remainingPlays = NUM_POS - numPlays;
				System.arraycopy(plays, numPlays, legalPlayLists[numPlays], 0, remainingPlays);
				int c2 = random.nextInt(remainingPlays);
				int play = legalPlayLists[numPlays][c2];
				Thread[] threads=new Thread[remainingPlays];
				int[] values=new int[NUM_POS]; //used to save all values of legal positions
				
				//use mutithreading to calculate the value of placing the card on each position
				for(int i=0;i<remainingPlays;i++){
					
					//If there's no card on the row or col, then value is 0 and no need to create thread
					Boolean zeroValue=true;
					for(int index=0;index<5;index++){
						if(grid[legalPlayLists[numPlays][i]/SIZE][index]==null&&grid[index][legalPlayLists[numPlays][i]%SIZE]==null)
							continue;
						else{
							zeroValue=false;
							break;
						}
					}
					if(zeroValue)
						values[legalPlayLists[numPlays][i]]=0;
					else{
						//create threads
						threads[i]=new Thread(new MyRunnable(card, grid,legalPlayLists[numPlays][i],values));
						threads[i].start();
					}
				}

				
				int maxValue=Integer.MIN_VALUE;
				ArrayList<Integer> maxValues = new ArrayList<Integer>(); // all positions yielding the maximum value 

				// await all threads to finish their work
				for(int i=0;i<threads.length;i++){
					if(threads[i]!=null)
						try {
							threads[i].join();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
				}

				//save all max values to maxValues[]
				for(int j=0;j<values.length;j++){
					if (values[j]>=maxValue&&values[j]!=0) {
						if(values[j]>maxValue){
							maxValues.clear();
						}
						maxValues.add(j);
						maxValue=values[j];
					}
				}
				//place the card on the position with the highest value
				play = maxValues.size()==0?play:maxValues.get(random.nextInt(maxValues.size())); // random select a position with the max value. If no max values, then use the random selected play
				makePlay(card, play / SIZE, play % SIZE);
			}			
			score = system.getScore(grid);

			// Undo MC plays.
			for (int d = 0; d < depth; d++) {
				undoPlay();
			}

			return score;
		}
	}
	
	public void makePlay(Card card, int row, int col) {
		// match simDeck to event
		int cardIndex = numPlays;
		while (!card.equals(simDeck[cardIndex]))
			cardIndex++;
		simDeck[cardIndex] = simDeck[numPlays];
		simDeck[numPlays] = card;
		
		// update plays to reflect chosen play in sequence
		grid[row][col] = card;
		int play = row * SIZE + col;
		int j = 0;
		while (plays[j] != play)
			j++;
		plays[j] = plays[numPlays];
		plays[numPlays] = play;
		
		// increment the number of plays taken
		numPlays++;
	}

	public void undoPlay() { // undo the previous play
		numPlays--;
		int play = plays[numPlays];
		grid[play / SIZE][play % SIZE] = null;	
	}

	//Get the value of the given card position
	public int getValue(Card[][] gridGiven, Card card, int[] cardPos){
        int difference = 0;
        int rowBefore, colBefore, rowAfter, colAfter; 

        int[] handScoresB = system.getHandScores(gridGiven);
		
        rowBefore = handScoresB[cardPos[0]];
        colBefore = handScoresB[cardPos[1]+SIZE];

        gridGiven[cardPos[0]][cardPos[1]] = card;

        int[] handScoresA = system.getHandScores(gridGiven);
        rowAfter = handScoresA[cardPos[0]];
        colAfter = handScoresA[cardPos[1]+SIZE];

        difference = rowAfter - rowBefore + colAfter - colBefore;
        return difference;
    }

	/* (non-Javadoc)
	 * @see PokerSquaresPlayer#setPointSystem(PokerSquaresPointSystem, long)
	 */
	@Override
	public void setPointSystem(PokerSquaresPointSystem system, long millis) {
		this.system = system;
	}

	/* (non-Javadoc)
	 * @see PokerSquaresPlayer#getName()
	 */
	@Override
	public String getName() {
		return "RandomMCPlayerDepth" + depthLimit;
	}

	/**
	 * Demonstrate RandomMCPlay with Ameritish point system.
	 * @param args (not used)
	 */
	public static void main(String[] args) {
		PokerSquaresPointSystem system = PokerSquaresPointSystem.getBritishPointSystem();
		System.out.println(system);
		new PokerSquares(new Yren2PokerSquaresPlayer(20), system).play(); // play a single game

	}

	public class MyRunnable implements Runnable {
		int[]values;
		Card card;
		Card[][] gridThread=new Card[5][5];
		int[]position=new int[2]; //[0] is row [1] is col
		int play;
		public MyRunnable( Card card,Card[][] gridClone,int play,int[] values) {
		   this.values=values;
		   this.card=card;

		   //make a new grid which copies values of the input grid, because each thread should have an independent grid
		   for (int i = 0; i < gridClone.length; i++)
				gridThread[i] = gridClone[i].clone();
		
		   this.play=play;
		   position[0]=play/SIZE;
		   position[1]=play%SIZE;

		}
  
		public void run() {
			//save the value in values[] using num play as the index
			values[this.play]=getValue(gridThread,this.card,this.position);
		}
	 }

}
