package com.example.earthonline.ui.tasks

import com.example.earthonline.data.local.entity.TaskEntity

/**
 * 任务树节点（用于 UI 渲染父子层级，对应 HTML 任务树的嵌套展示）。
 *
 * 除结构外还携带渲染层级所需的三类信息（都是构建时一次算好，避免组合期反复递归）：
 * - [depth]：缩进层级
 * - [isLast] / [ancestorLast]：绘制树形连接线所需
 * - [childTotal] / [childDone]：父任务上展示的子任务聚合进度
 */
data class TaskNode(
    val task: TaskEntity,
    val children: List<TaskNode>,
    val depth: Int,
    /** 自己在同层兄弟中是否是最后一个 */
    val isLast: Boolean = true,
    /**
     * 从根到第 depth-1 层，每一级祖先是否为其所在层的最后一个。
     * 长度恒等于 [depth]；用于判断某层的竖线是否需要向下贯穿（还有后续兄弟时贯穿）。
     */
    val ancestorLast: List<Boolean> = emptyList(),
    /** 整棵子树中的后代总数（不含自己） */
    val childTotal: Int = 0,
    /** 整棵子树中已完成的后代数 */
    val childDone: Int = 0
)

/**
 * 把扁平的任务列表构建成树（按 parentId 关联），根节点 depth=0。
 *
 * 采用后序累加：子节点的统计先算出，父节点直接 sumOf，整体 O(n) 而非每个节点重扫子树。
 * 找不到父节点（父被删 / 数据异常）的孤儿任务会被提升为根节点，避免整条分支消失。
 */
fun buildTaskTree(tasks: List<TaskEntity>): List<TaskNode> {
    val byParent = tasks.groupBy { it.parentId }
    val knownIds = tasks.mapTo(HashSet()) { it.id }

    fun node(task: TaskEntity, depth: Int, isLast: Boolean, ancestorLast: List<Boolean>): TaskNode {
        val kids = byParent[task.id].orEmpty()
        val childNodes = kids.mapIndexed { i, k ->
            node(k, depth + 1, i == kids.size - 1, ancestorLast + isLast)
        }
        // 后序累加：childTotal/childDone 已包含各自整棵子树
        val total = childNodes.sumOf { it.childTotal + 1 }
        val done = childNodes.sumOf { it.childDone + if (it.task.status == "done") 1 else 0 }
        return TaskNode(task, childNodes, depth, isLast, ancestorLast, total, done)
    }

    // 父 id 不存在于本列表时视为孤儿，按根节点处理
    val roots = byParent[null].orEmpty() +
        byParent.filterKeys { it != null && it !in knownIds }.values.flatten()

    return roots.mapIndexed { i, t -> node(t, 0, i == roots.size - 1, emptyList()) }
}

/** 把树拍平成带层级信息的列表（便于 LazyColumn 渲染，折叠的子树会被剪枝） */
fun flattenTaskTree(nodes: List<TaskNode>, collapsed: Set<String>): List<TaskNode> {
    val out = mutableListOf<TaskNode>()
    fun walk(list: List<TaskNode>) {
        for (n in list) {
            out.add(n)
            if (!collapsed.contains(n.task.id)) walk(n.children)
        }
    }
    walk(nodes)
    return out
}

/** 子树全部完成后代为「父任务已完成」的提示口径（仅用于展示，不写库） */
fun TaskNode.isSubtreeComplete(): Boolean = childTotal > 0 && childDone == childTotal
