package comfortable_andy.plain_warps.util.astar;

import org.bukkit.World;
import org.bukkit.util.BlockVector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;

public class AStarPathFinder {

    static double heuristicCurrentToEnd(AStarNode node, BlockVector goal) {
        return node.pos.distance(goal); // euclidean because diagonal is allowed (gpt-5)
    }

    static final double COST_ONE = 1.0, COST_TWO = Math.sqrt(2), COST_THREE = Math.sqrt(3);

    static double stepCost(BlockVector a, BlockVector b) {
        // pre-computed step cost due to neighbor area always being 3x3 (gpt-5)
        int axes =
                (a.getBlockX() != b.getBlockX() ? 1 : 0)
                        + (a.getBlockY() != b.getBlockY() ? 1 : 0)
                        + (a.getBlockZ() != b.getBlockZ() ? 1 : 0);
        return switch (axes) {
            case 1 -> COST_ONE;
            case 2 -> COST_TWO;
            case 3 -> COST_THREE;
            default ->
                    throw new IllegalStateException("stepCost received step delta with manhattan length outside of [1, 3]");
        };
    }

    public @Nullable List<BlockVector> findPath(World world, BlockVector start, BlockVector goal) {
        var open = new PriorityQueue<AStarNode>(128);
        var gScores = new HashMap<BlockVector, Double>();
        var allNodes = new HashMap<BlockVector, AStarNode>();
        var closed = new HashSet<BlockVector>(128);
        {
            AStarNode startNode = allNodes.computeIfAbsent(start, AStarNode::new);
            startNode.gScore = 0d;
            startNode.fScore = heuristicCurrentToEnd(startNode, goal);
            open.add(startNode);
            gScores.put(start, 0d);
        }
        AStarNode current;
        while (!open.isEmpty()) {
            current = open.poll();
            if (current.gScore
                    != gScores.getOrDefault(current.pos, Double.POSITIVE_INFINITY))
                continue; // stale/duplicate entry, skip (gpt-5)
            if (current.pos.equals(goal)) return reconstructPath(current);
            closed.add(current.pos);

            for (AStarNode neighbor : current.computeNeighbors(closed, allNodes, world)) {
                double tentativeG = current.gScore + stepCost(current.pos, neighbor.pos);
                double bestG = gScores.getOrDefault(neighbor.pos, Double.POSITIVE_INFINITY);

                if (bestG <= tentativeG)
                    continue; // no improvement found with this parent

                neighbor.parent = current; // the parent is ONLY set here (gpt-5)
                neighbor.gScore = tentativeG;
                neighbor.fScore = tentativeG + heuristicCurrentToEnd(neighbor, goal);
                gScores.put(neighbor.pos, neighbor.gScore);
                // don't remove and re-insert and allow duplicates (gpt-5)
                open.add(neighbor);
            }
        }
        return null;
    }

    static @NotNull List<BlockVector> reconstructPath(AStarNode end) {
        List<BlockVector> list = new ArrayList<>();
        AStarNode cur = end;
        while (cur != null) {
            list.addFirst(cur.pos);
            cur = cur.parent;
        }
        return list;
    }

}
