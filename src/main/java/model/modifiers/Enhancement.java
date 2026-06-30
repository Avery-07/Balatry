package model.modifiers;

public enum Enhancement {
    BONUS, // If card is played: adds 30 chips
    MULT, // If card is played: adds 5 mult
    WILD, // Card counts as any suit and can not be debuffed ever
    GLASS, // If card is played : multiplies mult by 2, 1/4 chance to break
    STEEL, // If card is held in hand : multiplies mult by 1.5
    STONE, // If card is held in hand : + 50 chips
    GOLD, // If card is held in hand at end of round : earns 3$
    LUCKY // If card is played : 1/5 chance to add 20 to mult, 1/10 chance to earn 10$
}
