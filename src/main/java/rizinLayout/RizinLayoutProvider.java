package rizinLayout;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.Map.Entry;

import javax.swing.Icon;

import ghidra.app.plugin.core.functiongraph.graph.layout.FGLayout;
import ghidra.app.plugin.core.functiongraph.graph.layout.AbstractFGLayout;
import ghidra.app.plugin.core.functiongraph.graph.layout.FGLayoutProviderExtensionPoint;
import ghidra.app.plugin.core.functiongraph.graph.FGEdge;
import ghidra.app.plugin.core.functiongraph.graph.FunctionGraph;
import ghidra.app.plugin.core.functiongraph.graph.vertex.FGVertex;
import ghidra.graph.VisualGraph;
import ghidra.graph.viewer.layout.*;
import ghidra.graph.viewer.vertex.VisualGraphVertexShapeTransformer;
import ghidra.util.exception.CancelledException;
import ghidra.util.task.TaskMonitor;
import resources.Icons;

public class RizinLayoutProvider extends FGLayoutProviderExtensionPoint {

	private static final String NAME = "Rizin Layout";

	@Override
	public String getLayoutName() {
		return NAME;
	}

	@Override
	public Icon getActionIcon() {
		return Icons.ARROW_UP_LEFT_ICON;
	}

	@Override
	public int getPriorityLevel() {
		// Just because it's your favorite layout out there
		return 1000;
	}

	@Override
	public FGLayout getFGLayout(FunctionGraph graph, TaskMonitor monitor)
			throws CancelledException {
		RizinLayout t = new RizinLayout(graph);
		t.setTaskMonitor(monitor);
		return t;
	}
	
	public String toString() {
		return NAME;
	}

	private class RizinLayout extends AbstractFGLayout {
		
		public LayoutState ls;

		protected RizinLayout(FunctionGraph graph) {
			super(graph, NAME);
		}

		@Override
		protected AbstractVisualGraphLayout<FGVertex, FGEdge> createClonedFGLayout(
				FunctionGraph newGraph) {
			return new RizinLayout(newGraph);
		}

		@Override
		protected Point2D getVertexLocation(FGVertex v, Column col, Row<FGVertex> row,
				Rectangle bounds) {
			return getCenteredVertexLocation(v, col, row, bounds);
		}

		@Override
		protected GridLocationMap<FGVertex, FGEdge> performInitialGridLayout(
				VisualGraph<FGVertex, FGEdge> g) throws CancelledException {

			GridLocationMap<FGVertex, FGEdge> gridLocations = new GridLocationMap<>();
			
			ls = new LayoutState(g);
			
			int entry_idx = 0;
			
			List<FGVertex> vertices = new ArrayList<FGVertex>(g.getVertices());
			for(int i = 0 ; i < vertices.size() ; i++) {
				FGVertex v = vertices.get(i);
				
				if (v.isEntry()) {
					entry_idx = i;
				}
				
				GridNode gn = new GridNode(v);
				ls.gridNodes.put(i, gn);
				ls.gridNodesR.put(v, i);
			}
			
			List<FGEdge> edges = new ArrayList<FGEdge>(g.getEdges());
			for(int i = 0 ; i < edges.size() ; i++) {
				FGEdge e = edges.get(i);
				
				GridEdge ge = new GridEdge(e);
				ge.from = ls.gridNodesR.get(e.getStart());
				ge.to = ls.gridNodesR.get(e.getEnd());
				ls.gridEdges.put(i, ge);
				ls.gridEdgesR.put(e, i);
			}
			
			ls.sorted_list = Toposort(entry_idx);
			
			assignRows();
			
			selectTree();
			findMergePoint();
			
			assignColumns();
			
			ls.columns = 1;
			ls.rows = 1;
			
			for (int i = 0 ; i < ls.gridNodes.size() ; i++) {
				GridNode gn = ls.gridNodes.get(i);
				ls.rows = ls.rows > (gn.row + 1) ? ls.rows : (gn.row + 1);
				ls.columns = ls.columns > (gn.col + 2) ? ls.columns : (gn.col + 2);
			}
			
			for (int i = 0; i < ls.gridNodes.size(); i++) {
				GridNode gn = ls.gridNodes.get(i);
				gridLocations.row(gn.v, gn.row);
				gridLocations.col(gn.v, gn.col);
			}

			return gridLocations;
		}
		
		private void assignColumns() {
			for (int i = 0 ; i < ls.sorted_list.size(); i++) {
				Integer idx = ls.sorted_list.get(i);
				GridNode gn = ls.gridNodes.get(idx);
				if (gn.treeEdges.size() == 0) {
					gn.row_count = 1;
					gn.col = 0;
					gn.lastRowRight = 2;
					gn.lastRowLeft = 0;
					gn.leftPosition = 0;
					gn.rightPosition = 2;
					gn.leftSideShape = Arrays.asList(0);
					gn.rightSideShape = Arrays.asList(2);
				}
				else {
					Integer firstChild_idx = gn.treeEdges.get(0);
					GridNode firstChild = ls.gridNodes.get(firstChild_idx);
					List<Integer> leftSide = firstChild.leftSideShape;
					List<Integer> rightSide = firstChild.rightSideShape;
					gn.row_count = firstChild.row_count;
					gn.lastRowRight = firstChild.lastRowRight;
					gn.lastRowLeft = firstChild.lastRowLeft;
					gn.leftPosition = firstChild.leftPosition;
					gn.rightPosition = firstChild.rightPosition;
					
					for (int j = 1; j < gn.treeEdges.size(); j++) {
						Integer child_idx = gn.treeEdges.get(j);
						GridNode child = ls.gridNodes.get(child_idx);
						int minPos = Integer.MIN_VALUE;
						int leftPos = 0;
						int rightPos = 0;
						Iterator<Integer> leftIt = rightSide.iterator();
						Iterator<Integer> rightIt = child.leftSideShape.iterator();
						int maxLeftWidth = 0;
						int minRightPos = child.col;
						int offsetCnt = 0;
						while (leftIt.hasNext() && rightIt.hasNext()) {
							leftPos += leftIt.next();
							rightPos += rightIt.next();
							minPos = minPos > (leftPos - rightPos) ? minPos : (leftPos - rightPos); // MAX
							maxLeftWidth = maxLeftWidth > leftPos ? maxLeftWidth : leftPos; // MAX
							minRightPos = minRightPos > rightPos ? rightPos : minRightPos; // MIN
							offsetCnt++;
						}
						// I chose here to do not implement tightSubtreePlacement (option)
						int rightTreeOffset = 0;
						if (leftIt.hasNext()) {
							rightTreeOffset = maxLeftWidth - child.leftPosition;
						}
						else {
							rightTreeOffset = gn.rightPosition - minRightPos;
						}
						child.col += rightTreeOffset;
						
						if (leftIt.hasNext()) {
							int val = rightSide.get(offsetCnt);
							rightSide.set(offsetCnt, val - (rightTreeOffset + child.lastRowRight - leftPos));
							int lst_size = rightSide.size();
							ArrayList<Integer> nl = new ArrayList<Integer>();
							nl.addAll(child.rightSideShape);
							nl.addAll(rightSide.subList(offsetCnt, lst_size));
							rightSide = nl;
						}
						else if (rightIt.hasNext()) {
							int val = child.leftSideShape.get(offsetCnt);
							child.leftSideShape.set(offsetCnt, val + (rightPos + rightTreeOffset - gn.lastRowLeft));
							int lst_size = child.leftSideShape.size();
							ArrayList<Integer> nl = new ArrayList<Integer>();
							nl.addAll(leftSide);
							nl.addAll(child.leftSideShape.subList(offsetCnt, lst_size));
							leftSide = nl;
							rightSide = child.rightSideShape;
							gn.lastRowRight = child.lastRowRight + rightTreeOffset;
							gn.lastRowLeft = child.lastRowLeft + rightTreeOffset;
						}
						else {
							rightSide = child.rightSideShape;
						}
						
						Integer head = rightSide.get(0);
						rightSide.set(0, head + rightTreeOffset);
						
						gn.row_count = gn.row_count > child.row_count ? gn.row_count : child.row_count;
						gn.leftPosition = gn.leftPosition > (child.leftPosition + rightTreeOffset) ? (child.leftPosition + rightTreeOffset) : gn.leftPosition;
						gn.rightPosition = gn.rightPosition > (rightTreeOffset + child.rightPosition) ? gn.rightPosition : (rightTreeOffset + child.rightPosition);
					}
					int col = 0;
					// Here we assume that parentBetweenDirectChild is set
					for (int j = 0; j < gn.treeEdges.size(); j++) {
						Integer child_idx = gn.treeEdges.get(j);
						GridNode child = ls.gridNodes.get(child_idx);
						col += child.col;
					}
					col /= gn.treeEdges.size();
					
					gn.col += col;
					gn.row_count++;
					gn.leftPosition = gn.leftPosition > gn.col ? gn.col : gn.leftPosition;
					gn.rightPosition = gn.rightPosition > (gn.col + 2) ? gn.rightPosition : (gn.col + 2);
					
					Integer head = leftSide.get(0);
					leftSide.set(0, head - gn.col);
					
					ArrayList<Integer> nl = new ArrayList<Integer>();
					nl.add(gn.col);
					nl.addAll(leftSide);
					gn.leftSideShape = nl;
					
					head = rightSide.get(0);
					rightSide.set(0,  head - (gn.col + 2));
					
					nl = new ArrayList<Integer>();
					nl.add(gn.col + 2);
					nl.addAll(rightSide);
					gn.rightSideShape = nl;
					
					for (int j = 0; j < gn.treeEdges.size(); j++) {
						Integer child_idx = gn.treeEdges.get(j);
						GridNode child = ls.gridNodes.get(child_idx);
						child.col -= gn.col;
					}
				}
			}
			
			int nextEmptyColumn = 0;
			for (int i = 0; i < ls.gridNodes.size(); i++) {
				GridNode gn = ls.gridNodes.get(i);
				if(gn.row == 0) {
					int offset = -gn.leftPosition;
					gn.col += nextEmptyColumn + offset;
					nextEmptyColumn = gn.rightPosition + offset + nextEmptyColumn;
				}
			}
			
			for (int i = ls.sorted_list.size() - 1 ; i >= 0 ; i--) {
				Integer idx = ls.sorted_list.get(i);
				GridNode nd = ls.gridNodes.get(idx);
				assert(nd.col >= 0);
				for (int j = 0 ; j < nd.treeEdges.size(); j++) {
					Integer child_idx = nd.treeEdges.get(j);
					GridNode child = ls.gridNodes.get(child_idx);
					child.col += nd.col;
				}
			}
			
		}

		private void findMergePoint() {
			for (int i = 0; i < ls.gridNodes.size(); i++) {
				GridNode gn = ls.gridNodes.get(i);
				int mergeBlock_idx = -1;
				int grandChildCount = 0;
				
				for (int j = 0 ; j < gn.treeEdges.size(); j++) {
					Integer target_idx = gn.treeEdges.get(j);
					GridNode targetGn = ls.gridNodes.get(target_idx);
					int tree_edges_size = targetGn.treeEdges.size();
					if (tree_edges_size != 0) {
						mergeBlock_idx = targetGn.treeEdges.get(0);
					}
					grandChildCount += tree_edges_size;
				}
				if (mergeBlock_idx == -1 || grandChildCount != 1) {
					continue;
				}
				
				int blockGoingToMerge = 0;
				int blockWithTreeEdge = 0;
				for (int j = 0 ; j < gn.treeEdges.size(); j++) {
					Integer target_idx = gn.treeEdges.get(j);
					GridNode targetGn = ls.gridNodes.get(target_idx);
					boolean goesToMerge = false;
					for (int k = 0 ; k < targetGn.dagEdges.size(); k++) {
						Integer target_dag_idx = targetGn.dagEdges.get(k);
						if (target_dag_idx == mergeBlock_idx) {
							goesToMerge = true;
							break;
						}
						if (goesToMerge) {
							if (targetGn.treeEdges.size() == 1) {
								blockWithTreeEdge = blockGoingToMerge;
							}
							blockGoingToMerge++;
						}
						else {
							break;
						}
					}
					if (blockGoingToMerge != 0) {
						ls.gridNodes.get(targetGn.treeEdges.get(blockWithTreeEdge)).col = blockWithTreeEdge * 2 - (blockGoingToMerge - 1);
					}
				}
					
			}
		}

		private void selectTree() {
			for (int i = 0; i < ls.gridNodes.size(); i++) {
				GridNode nd = ls.gridNodes.get(i);
				for (int j = 0 ; j < nd.dagEdges.size(); j++){
					Integer target_idx = nd.dagEdges.get(j);
					GridNode target = ls.gridNodes.get(target_idx);
					if (!target.hasParent && target.row == nd.row + 1) {
						nd.treeEdges.add(target_idx);
						target.hasParent = true;
					}	
				}
			}
		}

		private void assignRows() {
			for (int i = ls.sorted_list.size() - 1 ; i >= 0 ; i--) {
				Integer idx = ls.sorted_list.get(i);
				GridNode nd = ls.gridNodes.get(idx);
				int nextLvl = nd.row + 1;
				for (int j = 0 ; j < nd.dagEdges.size(); j++) {
					Integer target_idx = nd.dagEdges.get(j);
					GridNode target = ls.gridNodes.get(target_idx);
					target.row = target.row > nextLvl ? target.row : nextLvl;
				}
			}	
		}

		protected List<Integer> Toposort(int entry_idx) {
			List<Integer> blockOrder = new ArrayList<Integer>();
			int NotVisited = 0;
			int gn_size = ls.gridNodes.size();
			
			List<Integer> visited = new ArrayList<Integer>(gn_size);
			
			// Init the visited list with all 0 as blocks aren't visited yet
			for (int i = 0; i < gn_size; i++) {
				visited.add(0);
			}
			
			Stack<Map.Entry<Integer, Integer>> stack = new Stack<Map.Entry<Integer, Integer>>();
			
			Dfs(visited, stack, blockOrder, entry_idx);
			
			for (int i = 0 ; i < gn_size ; i++) {
				if (visited.get(i) == NotVisited) {
					Dfs(visited, stack, blockOrder, i);
				}
			}
			return blockOrder;	
		}
		
		void Dfs(List<Integer> visited, Stack<Entry<Integer, Integer>> stack, List<Integer> blockOrder, int entry_idx) {
			int NotVisited = 0, InStack = 1 , Visited = 2;
			
			visited.set(entry_idx, InStack);
			stack.push(new AbstractMap.SimpleEntry<Integer, Integer>(entry_idx, 0));
			
			while (!stack.isEmpty()) {
				Entry<Integer, Integer> elem = stack.lastElement();
				GridNode gn = ls.gridNodes.get(elem.getKey());
				int edge_index = elem.getValue();
								
				List<FGEdge> edges = new ArrayList<FGEdge>(ls.g.getOutEdges(gn.v));
				if (edge_index < edges.size()) {
					stack.lastElement().setValue(edge_index + 1);
					int target = ls.gridNodesR.get(edges.get(edge_index).getEnd());
					Integer targetState = visited.get(target);
					if (targetState.intValue() == NotVisited) {
						visited.set(target, InStack);
						stack.push(new AbstractMap.SimpleEntry<Integer, Integer>(target, 0));
						gn.dagEdges.add(target);
					}
					else if (targetState.intValue() == Visited) {
						gn.dagEdges.add(target);
					}
				}
				else {
					stack.pop();
					visited.set(elem.getKey(), Visited);
					blockOrder.add(elem.getKey());
				}
			}
		}

		@Override
		protected Map<FGEdge, List<Point2D>> positionEdgeArticulationsInLayoutSpace(
				VisualGraphVertexShapeTransformer<FGVertex> transformer,
				Map<FGVertex, Point2D> vertexLayoutLocations, Collection<FGEdge> edges,
				LayoutLocationMap<FGVertex, FGEdge> layoutLocations) throws CancelledException {

			Map<FGEdge, List<Point2D>> newEdgeArticulations = new HashMap<>();
			
			double ART_DISTANCE_FROM_NODE = 10;
			
			calculateEdgeMainColumn();
			
			
			List<Column> columns = new ArrayList<Column>(layoutLocations.columns());
			List<Row<FGVertex>> rows = new ArrayList<Row<FGVertex>>(layoutLocations.rows());
			
			Map<Integer, Integer> columnCountSegment = new HashMap<Integer, Integer>();
			Map<Integer, Integer> rowCountSegment = new HashMap<Integer, Integer>();
			
			for (int i = 0 ; i < ls.sorted_list.size(); i++) {
				GridNode toNode = ls.gridNodes.get(ls.sorted_list.get(i));
				List<FGEdge> inEdges = new ArrayList<FGEdge>(ls.g.getInEdges(toNode.v));
				for (int j = 0 ; j < inEdges.size(); j++)
				{
					FGEdge f_edge = inEdges.get(j);
					Integer edge_idx = ls.gridEdgesR.get(f_edge);
					GridEdge edge = ls.gridEdges.get(edge_idx);
					
					GridNode fromNode = ls.gridNodes.get(edge.from);
					int mainCol = edge.main_col;
					
					Point2D fromLoc = vertexLayoutLocations.get(fromNode.v);
					double fromBottom = rows.get(fromNode.row).y + rows.get(fromNode.row).height;
					
					Point2D toLoc = vertexLayoutLocations.get(toNode.v);
					double toTop = rows.get(toNode.row).y;
					
					List<Point2D> articulations = new ArrayList<>();
					double x1, y1;
					
					int OFFSET_FROM_NODE_VER = 10;
					int SIZE_BETWEEN_NODE_VER = 50;
					int MAX_NUMBER_SEGMENT_PER_COL = 15;
					int DEFAULT_SPACE_BETWEEN_SEG = 3;
					int MAX_NUMBER_SEGMENT_PER_ROW = (SIZE_BETWEEN_NODE_VER - OFFSET_FROM_NODE_VER) / DEFAULT_SPACE_BETWEEN_SEG;
					
					
					double spaceBetweenInEdgeHor = columns.get(toNode.col).getPaddedWidth(false) / (inEdges.size() + 1);					
					
					if (mainCol == fromNode.col && mainCol == toNode.col) {
						// NOTHING TO DO ?? THE EDGE GOES IN A STRAIGHT LINE, NO ARTICULATIONS NEEDED
					}
					else if (mainCol != fromNode.col && mainCol == toNode.col) {
						// IN THIS CASE WE JUST HAVE TO MOVE TO MAIN COL
						int direction = mainCol > fromNode.col ? 1 : -1;
						
						Integer n = rowCountSegment.computeIfPresent(fromNode.row, (k, v) -> (v + 1) > MAX_NUMBER_SEGMENT_PER_ROW ? MAX_NUMBER_SEGMENT_PER_ROW : v + 1);
						if (n == null) {
							rowCountSegment.put(fromNode.row, 1);
							n = 1;
						}
						
						x1 = fromLoc.getX() + direction * ART_DISTANCE_FROM_NODE;
						y1 = fromBottom + OFFSET_FROM_NODE_VER + (SIZE_BETWEEN_NODE_VER - OFFSET_FROM_NODE_VER) * n / MAX_NUMBER_SEGMENT_PER_ROW;
						articulations.add(new Point2D.Double(x1, y1));
						
						x1 = toLoc.getX();
						articulations.add(new Point2D.Double(x1, y1));	
					}
					else if (mainCol == fromNode.col && mainCol != toNode.col) {
						// IN THIS CASE WE JUST MOVE UP OR DOWN THEN MOVE TO MAIN COL
						
						Integer n = rowCountSegment.computeIfPresent(toNode.row - 1, (k, v) -> (v + 1) > MAX_NUMBER_SEGMENT_PER_ROW ? MAX_NUMBER_SEGMENT_PER_ROW : v + 1);
						if (n == null) {
							rowCountSegment.put(toNode.row - 1, 1);
							n = 1;
						}
						
						int direction = mainCol < toNode.col ? 1 : -1;
						x1 = fromLoc.getX() + direction * ART_DISTANCE_FROM_NODE;
						y1 = toTop - SIZE_BETWEEN_NODE_VER + OFFSET_FROM_NODE_VER + ((SIZE_BETWEEN_NODE_VER - OFFSET_FROM_NODE_VER) * n / MAX_NUMBER_SEGMENT_PER_ROW);
						articulations.add(new Point2D.Double(x1, y1));
						
						x1 = toLoc.getX() + (-1 * direction * 5) + (-1 * direction * j * spaceBetweenInEdgeHor);
						articulations.add(new Point2D.Double(x1, y1));
						
					}
					else {
						// IN THIS LAST CASE WE NEED 4 ARTICULATIONS! 
						// MOVE TO MAIN COLUMN
						int direction = mainCol > fromNode.col ? 1 : -1;
						
						Integer n = rowCountSegment.computeIfPresent(fromNode.row, (k, v) -> (v + 1) > MAX_NUMBER_SEGMENT_PER_ROW ? MAX_NUMBER_SEGMENT_PER_ROW : v + 1);
						if (n == null) {
							rowCountSegment.put(fromNode.row, 1);
							n = 1;
						}
						
						x1 = fromLoc.getX() + direction * ART_DISTANCE_FROM_NODE;
						y1 = fromBottom + OFFSET_FROM_NODE_VER + (SIZE_BETWEEN_NODE_VER - OFFSET_FROM_NODE_VER) * n / MAX_NUMBER_SEGMENT_PER_ROW;
						articulations.add(new Point2D.Double(x1, y1));
													
						n = columnCountSegment.computeIfPresent(mainCol, (k, v) -> (v + 1) > MAX_NUMBER_SEGMENT_PER_COL ? MAX_NUMBER_SEGMENT_PER_COL : v + 1);
						if (n == null) {
							columnCountSegment.put(mainCol, 1);
							n = 1;
						}
						
						if (mainCol == -1) {
							Column firstCol = columns.get(0);
							x1 = firstCol.x - (firstCol.getPaddedWidth(false) >> 2) - n * DEFAULT_SPACE_BETWEEN_SEG;
						}
						else if (mainCol == columns.size()) {
							Column lastCol = columns.get(columns.size() - 1);
							x1 = lastCol.x + 5 * (lastCol.getPaddedWidth(false) >> 2) + n * DEFAULT_SPACE_BETWEEN_SEG;
						}
						else {
							Column destcol = columns.get(mainCol);
							x1 = destcol.x + destcol.getPaddedWidth(false) * n / MAX_NUMBER_SEGMENT_PER_COL;		
						}
						
						articulations.add(new Point2D.Double(x1, y1));
						
						n = rowCountSegment.computeIfPresent(toNode.row - 1, (k, v) -> (v + 1) > MAX_NUMBER_SEGMENT_PER_ROW ? MAX_NUMBER_SEGMENT_PER_ROW : v + 1);
						if (n == null) {
							rowCountSegment.put(toNode.row - 1, 1);
							n = 1;
						}
						
						// THEN MOVE TO TO COLUMN
						direction = mainCol < toNode.col ? 1 : -1;
						
						y1 = toTop - SIZE_BETWEEN_NODE_VER + OFFSET_FROM_NODE_VER + ((SIZE_BETWEEN_NODE_VER - OFFSET_FROM_NODE_VER) * n / MAX_NUMBER_SEGMENT_PER_ROW);
						articulations.add(new Point2D.Double(x1, y1));
						
						x1 = toLoc.getX() + (-1 * direction * 5) + -1 * direction * j * spaceBetweenInEdgeHor;
						articulations.add(new Point2D.Double(x1, y1));
					}
					
					newEdgeArticulations.put(edge.e, articulations);
				}
			}
			
			
			return newEdgeArticulations;
		}

		private void calculateEdgeMainColumn() {
			List<Event> events = new ArrayList<Event>();
			
			for (int i = 0; i < ls.gridNodes.size(); i++) {
				GridNode gn = ls.gridNodes.get(i);
				events.add(new Event(i, 0, gn.row, 1));
				
				int startRow = gn.row;
				
				List<FGEdge> outEdges = new ArrayList<FGEdge>(ls.g.getOutEdges(gn.v));
				for (int j = 0; j < outEdges.size(); j++) {
					FGEdge edge = outEdges.get(j);
					Integer target_idx = ls.gridNodesR.get(edge.getEnd());
					GridNode target = ls.gridNodes.get(target_idx);
					int endRow = target.row;
					int edgeIdx = ls.gridEdgesR.get(edge);
					events.add(new Event(i, edgeIdx, startRow > endRow ? startRow : endRow, 0));
				}
			}
			
			Collections.sort(events, new Comparator<Event>(){
				public int compare(Event o1, Event o2) {
					if (o1.row != o2.row) {
						return o1.row - o2.row;	
					}
					return o1.type - o2.type;
				  }
			});
			
			List<Integer> blockedColumns = new ArrayList<Integer>();
			for (int i = 0; i < ls.columns; i++) {
				blockedColumns.add(-1);
			}
			
			for (int i = 0 ; i < events.size(); i++) {
				Event event = events.get(i);
				if (event.type == 1) {
					int col = ls.gridNodes.get(event.node_id).col;
					blockedColumns.set(col, event.row);
				}
				else {
					GridNode gn = ls.gridNodes.get(event.node_id);
					GridEdge edge = ls.gridEdges.get(event.edge_id);
					int col = gn.col;
					Integer target_idx = edge.to;
					GridNode target = ls.gridNodes.get(target_idx);
					int topRow = gn.row > target.row ? target.row : gn.row;
					int targetColumn = target.col;
					
					if (blockedColumns.get(col).intValue() <= topRow) {
						edge.main_col = col;
					}
					else if (blockedColumns.get(targetColumn).intValue() <= topRow) {
						edge.main_col = targetColumn;
					}
					else {
						int nearestLeft = leftMostLessThan(blockedColumns, col, topRow);
						int nearestRight = rightMostLessThan(blockedColumns, col, topRow);
												
						int distanceLeft = col - nearestLeft + Math.abs(targetColumn - nearestLeft);
						int distanceRight = nearestRight - col + Math.abs(targetColumn - nearestRight);
						
						if (target.row < gn.row) {
							if (targetColumn < col && blockedColumns.get(col).intValue() < topRow && col - targetColumn <= distanceLeft) {
								edge.main_col = col;
								continue;
							}
							else if (targetColumn > col && blockedColumns.get(col).intValue() < topRow && targetColumn - col <= distanceRight) {
								edge.main_col = col;
								continue;
							}
						}
						
						if (distanceLeft != distanceRight) {
							edge.main_col = distanceLeft < distanceRight ? nearestLeft : nearestRight;
						}
						else {
							// LOL
							edge.main_col = (event.edge_id % 2 == 0) ? nearestLeft : nearestRight;
						}
					}
				}
				
			}		
		}

		private int leftMostLessThan(List<Integer> blockedColumns, int col, int topRow) {
			for (int i = col; i >= 0; i--) {
				if (blockedColumns.get(i) < topRow) {
					return i;
				}
			}
			return -1;
		}
		
		private int rightMostLessThan(List<Integer> blockedColumns, int col, int topRow) {
			for (int i = col; i < blockedColumns.size(); i++) {
				if (blockedColumns.get(i) < topRow) {
					return i;
				}
			}
			return blockedColumns.size();
		}
	}
	
	private class LayoutState {
		public List<Integer> sorted_list;
		public int rows;
		public int columns;
		Map<Integer, GridNode> gridNodes;
		Map<FGVertex, Integer> gridNodesR;
		Map<Integer, GridEdge> gridEdges;
		Map<FGEdge, Integer> gridEdgesR;
		VisualGraph<FGVertex, FGEdge> g;
		
		LayoutState(VisualGraph<FGVertex, FGEdge> g){
			this.g = g;
			this.gridNodes = new HashMap<Integer, GridNode>();
			this.gridNodesR = new HashMap<FGVertex, Integer>();
			this.gridEdges = new HashMap<Integer, GridEdge>();
			this.gridEdgesR = new HashMap<FGEdge, Integer>();
		}
	}

	private class GridNode {

		public int rightPosition;
		public List<Integer> rightSideShape;
		public List<Integer> leftSideShape;
		public int leftPosition;
		public int lastRowLeft;
		public int lastRowRight;
		public int row_count;
		public int row = 0;
		public int col = 0;
		public boolean hasParent = false;
		private FGVertex v;
		private List<Integer> dagEdges;
		private List<Integer> treeEdges;

		GridNode(FGVertex v) {
			this.v = v;
			this.dagEdges = new ArrayList<Integer>();
			this.treeEdges = new ArrayList<Integer>();
		}

	}
	
	private class GridEdge {

		private FGEdge e;
		int from = -1;
		int to = -1;
		int main_col;

		GridEdge(FGEdge e) {
			this.e = e;
		}
	}
	
	private class Event{

		int node_id;
		int edge_id;
		int row;
		int type; // Edge = 0; Block = 1

		Event(int nid, int eid, int row, int t) {
			this.node_id = nid;
			this.edge_id = eid;
			this.row = row;
			this.type = t;
		}
	}
}
