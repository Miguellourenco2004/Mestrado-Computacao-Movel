package com.example.minequest.model

object QuestCatalog {

    val allQuests = listOf(
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
            type = QuestType.WALK_DISTANCE,
            target = 3000, // 3km
            reward = 100,
            description = "Walk 3Km"
        ),
        DailyQuest(
            id = "Q04",
            type = QuestType.WALK_DISTANCE,
            target = 7000, // 7km
            reward = 200,
            description = "Walk 7Km"
        ),
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
        )
    )

    fun getRandomQuest(): DailyQuest {
        return allQuests.random()
    }
}