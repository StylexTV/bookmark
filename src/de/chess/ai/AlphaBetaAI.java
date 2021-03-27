package de.chess.ai;

import de.chess.game.Board;
import de.chess.game.KillerTable;
import de.chess.game.Move;
import de.chess.game.MoveGenerator;
import de.chess.game.MoveList;
import de.chess.game.TranspositionEntry;
import de.chess.game.TranspositionTable;
import de.chess.game.Winner;
import de.chess.ui.WidgetUI;
import de.chess.util.MathUtil;

public class AlphaBetaAI {
	
	private static final int INFINITY = 100000;
	
	private static final int MAX_DEPTH = 6; // 6
	private static final int MAX_QUIESCE_DEPTH = 27; // 27
	private static final int MAX_CHECKING_MOVES_DEPTH = 17; // 17
	
	private static Move responseMove;
	
	private static long visitedNormalNodes;
	private static long visitedQuiesceNodes;
	private static int maxDepth;
	private static int transpositionUses;
	
	public static Move findNextMove(Board b) {
		long before = System.currentTimeMillis();
		
		visitedNormalNodes = 0;
		visitedQuiesceNodes = 0;
		maxDepth = 0;
		transpositionUses = 0;
		
		int score = runAlphaBeta(b, -INFINITY, INFINITY);
		
		float time = (System.currentTimeMillis() - before) / 1000f;
		
		long visitedNodes = visitedNormalNodes + visitedQuiesceNodes;
		
		System.out.println("---");
		System.out.println("time: "+MathUtil.DECIMAL_FORMAT.format(time)+"s");
		System.out.println("max_depth: "+MathUtil.DECIMAL_FORMAT.format(maxDepth));
		System.out.println("prediction: "+MathUtil.DECIMAL_FORMAT.format(score));
		System.out.println("visited_nodes: "+MathUtil.DECIMAL_FORMAT.format(visitedNodes));
		System.out.println("nodes_per_second: "+MathUtil.DECIMAL_FORMAT.format(visitedNodes / time));
		System.out.println("visited_normal_nodes: "+MathUtil.DECIMAL_FORMAT.format(visitedNormalNodes));
		System.out.println("visited_quiesce_nodes: "+MathUtil.DECIMAL_FORMAT.format(visitedQuiesceNodes));
		System.out.println("transposition_uses: "+MathUtil.DECIMAL_FORMAT.format(transpositionUses));
		
		WidgetUI.setPrediction(score);
		
		return responseMove;
	}
	
	private static int runAlphaBeta(Board b, int alpha, int beta) {
		int originalAlpha = alpha;
		
		visitedNormalNodes++;
		
		MoveList list = new MoveList();
		
		MoveGenerator.generateAllMoves(b, list);
		
		MoveEvaluator.eval(list, b);
		
		TranspositionEntry entry = TranspositionTable.getEntry(b.getPositionKey());
		
		if(entry != null && entry.getMove() != null) {
			list.applyMoveScore(entry.getMove(), MoveEvaluator.HASH_MOVE_SCORE);
		}
		
		applyKillerMoves(list, b.getHistoryPly());
		
		Move bestMove = null;
		
		while(list.hasMovesLeft()) {
			Move m = list.next();
			
			b.makeMove(m);
			
			if(!b.isOpponentInCheck()) {
				int score = -alphaBeta(b, -beta, -alpha, 1);
				
				if(score > alpha) {
					bestMove = m;
					alpha = score;
				}
			}
			
			b.undoMove(m);
		}
		
		responseMove = bestMove;
		
//		TranspositionTable.storeEntry(b.getPositionKey(), 0, bestMove, originalAlpha, beta, alpha, b.getHistoryPly());
		
		return alpha;
	}
	
	private static int alphaBeta(Board b, int alpha, int beta, int depth) {
		if(depth > maxDepth) maxDepth = depth;
		
		if(depth == MAX_DEPTH) {
			return quiesce(b, alpha, beta, depth);
		}
		
		visitedNormalNodes++;
		
		if(b.getFiftyMoveCounter() == 100 || b.hasThreefoldRepetition()) return 0;
		
		int originalAlpha = alpha;
		
		TranspositionEntry entry = TranspositionTable.getEntry(b.getPositionKey());
		
		if(entry != null && entry.getDepth() <= depth) {
			transpositionUses++;
			
			if(entry.getType() == TranspositionEntry.TYPE_EXACT) return entry.getScore();
			else if(entry.getType() == TranspositionEntry.TYPE_LOWER_BOUND) alpha = Math.max(alpha, entry.getScore());
			else beta = Math.min(beta, entry.getScore());
			
			if(alpha >= beta) return entry.getScore();
		}
		
		MoveList list = new MoveList();
		
		MoveGenerator.generateAllMoves(b, list);
		
		MoveEvaluator.eval(list, b);
		
		if(entry != null && entry.getMove() != null) {
			list.applyMoveScore(entry.getMove(), MoveEvaluator.HASH_MOVE_SCORE);
		}
		
		applyKillerMoves(list, b.getHistoryPly());
		
		boolean hasLegalMove = false;
		
		Move bestMove = null;
		int bestScore = 0;
		
		while(list.hasMovesLeft()) {
			Move m = list.next();
			
			b.makeMove(m);
			
			if(!b.isOpponentInCheck()) {
				hasLegalMove = true;
				
				int score = -alphaBeta(b, -beta, -alpha, depth + 1);
				
				if(score > alpha) {
					alpha = score;
					
					if(bestMove == null || score > bestScore) {
						bestMove = m;
						bestScore = score;
					}
				}
			}
			
			b.undoMove(m);
			
			if(alpha >= beta) {
				KillerTable.storeMove(m, b.getHistoryPly());
				
//				TranspositionTable.putEntry(b.getPositionKey(), 0, bestMove, TranspositionEntry.TYPE_LOWER_BOUND, alpha, b.getHistoryPly());
				
				return alpha;
			}
		}
		
		if(!hasLegalMove) {
			int winner = b.findWinner(false);
			
			if(winner == Winner.DRAW) {
				return 0;
			} else {
				int score = INFINITY - depth;
				
				return b.getSide() == winner ? score : -score;
			}
		}
		
//		TranspositionTable.storeEntry(b.getPositionKey(), depth, bestMove, originalAlpha, beta, alpha, b.getHistoryPly());
		
		return alpha;
	}
	
	private static int quiesce(Board b, int alpha, int beta, int depth) {
		visitedQuiesceNodes++;
		if(depth > maxDepth) maxDepth = depth;
		
		if(b.getFiftyMoveCounter() == 100 || b.hasThreefoldRepetition()) return 0;
		
		int originalAlpha = alpha;
		
		TranspositionEntry entry = TranspositionTable.getEntry(b.getPositionKey());
		
		if(entry != null && entry.getDepth() <= depth) {
			transpositionUses++;
			
			if(entry.getType() == TranspositionEntry.TYPE_EXACT) return entry.getScore();
			else if(entry.getType() == TranspositionEntry.TYPE_LOWER_BOUND) alpha = Math.max(alpha, entry.getScore());
			else beta = Math.min(beta, entry.getScore());
			
			if(alpha >= beta) return entry.getScore();
		}
		
		boolean inCheck = b.isSideInCheck();
		
		if(!inCheck) {
			int evalScore = Evaluator.eval(b);
			
			if(evalScore >= beta) return beta;
			
			if(depth >= MAX_QUIESCE_DEPTH) {
				return evalScore;
			}
			
			if(evalScore > alpha) alpha = evalScore;
		}
		
		MoveList list = new MoveList();
		
		MoveGenerator.generateAllMoves(b, list);
		
		MoveEvaluator.eval(list, b);
		
		if(entry != null && entry.getMove() != null) {
			list.applyMoveScore(entry.getMove(), MoveEvaluator.HASH_MOVE_SCORE);
		}
		
		applyKillerMoves(list, b.getHistoryPly());
		
		MoveList checkingMoves = new MoveList();
		
		boolean allowCheckingMoves = depth < MAX_CHECKING_MOVES_DEPTH;
		
		boolean hasLegalMove = false;
		boolean hasAlphaRisen = false;
		
		Move bestMove = null;
		int bestScore = 0;
		
		while(list.hasMovesLeft()) {
			Move m = list.next();
			
			b.makeMove(m);
			
			int score = 0;
			boolean hasDoneMove = false;
			
			if(!b.isOpponentInCheck()) {
				hasLegalMove = true;
				
				if(inCheck || m.getCaptured() != 0) {
					hasDoneMove = true;
					
					score = -quiesce(b, -beta, -alpha, depth + 1);
					
					if(score > alpha) {
						alpha = score;
						
						hasAlphaRisen = true;
						
						if(bestMove == null || score > bestScore) {
							bestMove = m;
							bestScore = score;
						}
					}
				} else if(allowCheckingMoves && !inCheck && b.isSideInCheck()) {
					checkingMoves.addMove(m);
				}
			}
			
			b.undoMove(m);
			
			if(hasDoneMove && score >= beta) {
				KillerTable.storeMove(m, b.getHistoryPly());
				
//				TranspositionTable.putEntry(b.getPositionKey(), 0, bestMove, TranspositionEntry.TYPE_LOWER_BOUND, alpha, b.getHistoryPly());
				
				return beta;
			}
		}
		
		if(!hasLegalMove) {
			int winner = b.findWinner(false);
			
			if(winner == Winner.DRAW) {
				return 0;
			} else {
				int score = INFINITY - depth;
				
				return b.getSide() == winner ? score : -score;
			}
		}
		
		if(allowCheckingMoves && !inCheck && !hasAlphaRisen) {
			checkingMoves.reset();
			
			while(checkingMoves.hasMovesLeft()) {
				Move m = checkingMoves.next();
				
				b.makeMove(m);
				
				int score = -quiesce(b, -beta, -alpha, depth + 1);
				
				if(score > alpha) {
					alpha = score;
					
					if(bestMove == null || score > bestScore) {
						bestMove = m;
						bestScore = score;
					}
				}
				
				b.undoMove(m);
				
				if(score >= beta) {
					KillerTable.storeMove(m, b.getHistoryPly());
					
					return beta;
				}
			}
		}
		
//		TranspositionTable.storeEntry(b.getPositionKey(), depth, bestMove, originalAlpha, beta, alpha, b.getHistoryPly());
		
		return alpha;
	}
	
	private static void applyKillerMoves(MoveList list, int ply) {
		for(int i=0; i<KillerTable.SIZE; i++) {
			Move killer = KillerTable.getMove(ply, i);
			
			if(killer != null) list.applyMoveScore(killer, MoveEvaluator.KILLER_MOVE_SCORE);
		}
	}
	
}
