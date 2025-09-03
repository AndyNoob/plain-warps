package comfortable_andy.plain_warps.util.astar;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor
@Getter
public class AStarNode implements Comparable<AStarNode> {
    @EqualsAndHashCode.Include
    public final BlockVector pos;
    public @Nullable AStarNode parent;
    public double gScore, fScore;

    @Override
    public int compareTo(@NotNull AStarNode o) {
        int c = Double.compare(fScore, o.fScore);
        if (c != 0) return c;
        // prefer larger g (deeper), this often helps as a heuristic tie-breaker (gpt-5)
        return Double.compare(o.gScore, this.gScore);
    }

    /**
     * This method guarantees that each neighbor is not closed
     * and that each neighbor is a valid node. A node is considered a
     * neighbor if it is within a 3x3x3 area with this node
     * as the center.
     *
     * @param closed   a set of positions that are already evaluated
     * @param allNodes the map of all nodes that were ever created
     * @param world    to check validness of potential nodes
     * @return neighbors
     */
    public List<AStarNode> computeNeighbors(Collection<BlockVector> closed, Map<BlockVector, AStarNode> allNodes, World world) {
        var nodes = new ArrayList<AStarNode>();
        for (int x = -1; x < 2; x++) {
            for (int y = -1; y < 2; y++) {
                for (int z = -1; z < 2; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    var newPos = pos.clone().add(new Vector(x, y, z)).toBlockVector();
                    if (closed.contains(newPos)) continue;
                    if (isValid(world, newPos)) {
                        AStarNode node = allNodes.computeIfAbsent(newPos, AStarNode::new);
                        // parent is intentionally left null for the caller (gpt-5)
                        nodes.add(node);
                    }
                }
            }
        }
        return nodes;
    }

    public static boolean isValid(World world, BlockVector pos) {
        Block block = pos.toLocation(world).getBlock();
        return !block.isSolid() && block.getRelative(0, -1, 0).isSolid();
    }

}
