package ie.griffith.plantlight.data

/** Built-in catalogue of houseplants and the light each one prefers. */
object PlantCatalog {
    val ALL: List<Plant> = listOf(
        Plant(name = "Snake Plant", lightNeed = "LOW",
            notes = "Tolerates shade and infrequent watering."),
        Plant(name = "ZZ Plant", lightNeed = "LOW",
            notes = "Very low-light tolerant; keep the soil dry."),
        Plant(name = "Pothos", lightNeed = "LOW",
            notes = "Trailing vine that copes well with a dim corner."),
        Plant(name = "Peace Lily", lightNeed = "LOW",
            notes = "Low light; likes evenly moist soil."),
        Plant(name = "Cast Iron Plant", lightNeed = "LOW",
            notes = "Extremely shade tolerant and hard to kill."),
        Plant(name = "Chinese Evergreen", lightNeed = "LOW",
            notes = "Happy in low to medium indoor light."),
        Plant(name = "Spider Plant", lightNeed = "MEDIUM",
            notes = "Easy care; medium, indirect light."),
        Plant(name = "Monstera", lightNeed = "MEDIUM",
            notes = "Medium to bright indirect light; avoid harsh midday sun."),
        Plant(name = "Heartleaf Philodendron", lightNeed = "MEDIUM",
            notes = "Reliable in medium indoor light."),
        Plant(name = "Dracaena", lightNeed = "MEDIUM",
            notes = "Medium, indirect light; let the soil dry a little."),
        Plant(name = "Boston Fern", lightNeed = "MEDIUM",
            notes = "Medium light and higher humidity."),
        Plant(name = "Prayer Plant", lightNeed = "MEDIUM",
            notes = "Medium indirect light; keep out of direct sun."),

        Plant(name = "Fiddle Leaf Fig", lightNeed = "BRIGHT",
            notes = "Needs bright, indirect light, often near a window."),
        Plant(name = "Aloe Vera", lightNeed = "BRIGHT",
            notes = "Bright light, with some direct sun if possible."),
        Plant(name = "Jade Plant", lightNeed = "BRIGHT",
            notes = "A succulent that wants a bright windowsill."),
        Plant(name = "Croton", lightNeed = "BRIGHT",
            notes = "Bright light keeps the colourful leaves vivid."),
        Plant(name = "Basil", lightNeed = "BRIGHT",
            notes = "Herbs thrive in a bright, sunny kitchen window."),
        Plant(name = "Echeveria", lightNeed = "BRIGHT",
            notes = "A succulent that needs bright light to stay compact.")
    )
}
