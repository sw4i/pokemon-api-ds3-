package stadium;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Modelo de un Pokémon: datos de PokeAPI (stats base, tipos, sprite) y su HP actual en combate. */
public class Pokemon {
    private final String name;
    private final String spriteUrl;
    private final List<String> types;
    private final int maxHp;
    private final int attack;
    private final int defense;
    private final int speed;
    private int currentHp;

    public Pokemon(String name, String spriteUrl, List<String> types, int maxHp, int attack, int defense, int speed) {
        this.name = name;
        this.spriteUrl = spriteUrl;
        this.types = Collections.unmodifiableList(new ArrayList<>(types));
        this.maxHp = maxHp;
        this.attack = attack;
        this.defense = defense;
        this.speed = speed;
        this.currentHp = maxHp;
    }

    public String getName() {
        return name;
    }

    public String getSpriteUrl() {
        return spriteUrl;
    }

    public List<String> getTypes() {
        return types;
    }

    public int getMaxHp() {
        return maxHp;
    }

    public int getAttack() {
        return attack;
    }

    public int getDefense() {
        return defense;
    }

    public int getSpeed() {
        return speed;
    }

    public int getCurrentHp() {
        return currentHp;
    }

    public void resetHp() {
        currentHp = maxHp;
    }

    public void applyDamage(int damage) {
        currentHp = Math.max(0, currentHp - Math.max(0, damage));
    }

    public boolean isFainted() {
        return currentHp <= 0;
    }

    public String getPrimaryType() {
        return types.isEmpty() ? "normal" : types.get(0).toLowerCase();
    }
}
