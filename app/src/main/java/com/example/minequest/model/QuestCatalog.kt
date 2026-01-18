package com.example.minequest.model

object QuestCatalog {

    val allQuests = listOf(
        // ────────────── MINE BLOCKS ──────────────
        DailyQuest(
            id = "Q01",
            type = QuestType.MINE_BLOCKS,
            target = 30,
            reward = 100,
            description = "Mine 30 blocks"
        ),
        DailyQuest(
            id = "Q02",
            type = QuestType.MINE_BLOCKS,
            target = 100,
            reward = 500,
            description = "Mine 100 blocks"
        ),
        DailyQuest(
            id = "Q03",
            type = QuestType.MINE_BLOCKS,
            target = 200,
            reward = 900,
            description = "Mine 200 blocks"
        ),
        DailyQuest(
            id = "Q04",
            type = QuestType.MINE_BLOCKS,
            target = 500,
            reward = 2000,
            description = "Mine 500 blocks"
        ),

        // ────────────── TRADE ──────────────
        DailyQuest(
            id = "Q05",
            type = QuestType.TRADE,
            target = 1,
            reward = 80,
            description = "Make one trade in the global chat"
        ),
        DailyQuest(
            id = "Q06",
            type = QuestType.TRADE,
            target = 3,
            reward = 80,
            description = "Make three trades in the global chat"
        ),
        DailyQuest(
            id = "Q07",
            type = QuestType.TRADE,
            target = 5,
            reward = 150,
            description = "Make five trades in the global chat"
        ),
        DailyQuest(
            id = "Q08",
            type = QuestType.TRADE,
            target = 10,
            reward = 350,
            description = "Make ten trades in the global chat"
        )
    )


    fun getRandomQuest(): DailyQuest {
        return allQuests.random()
    }
}