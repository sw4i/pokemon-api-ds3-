package stadium;

import java.util.Random;

/**
 * Reglas del combate por turnos. No conoce la interfaz: toda la información sale
 * a través de {@link BattleListener}.
 *
 * <p>Fórmula de daño elegida:
 * <pre>
 * base  = max(1, ATK * rand(0.85..1.15) - DEF * rand(0.35..0.60))
 * daño  = max(1, round(base * crítico * efectividad))
 * crítico     = 1.5 con 10% de probabilidad, si no 1.0
 * efectividad = 1.3 / 0.7 / 1.0 según el primer tipo (Agua > Fuego > Planta > Agua)
 * </pre>
 * Se acotan los factores aleatorios para que el ataque siempre pese más que la defensa
 * y el combate no se estanque con daños de 0.
 */
public class Battle {
    private static final long TURN_DELAY_MS = 700;
    private static final double CRITICAL_CHANCE = 0.10;
    private static final double CRITICAL_MULTIPLIER = 1.5;
    private static final double SUPER_EFFECTIVE = 1.3;
    private static final double NOT_EFFECTIVE = 0.7;

    private final Random random = new Random();
    private final Pokemon left;
    private final Pokemon right;
    private final String leftName;
    private final String rightName;

    public Battle(Pokemon left, Pokemon right) {
        this.left = left;
        this.right = right;
        // Si ambos jugadores eligen el mismo Pokémon, los nombres de los eventos deben distinguirse.
        boolean sameName = left.getName().equals(right.getName());
        this.leftName = sameName ? left.getName() + " (J1)" : left.getName();
        this.rightName = sameName ? right.getName() + " (J2)" : right.getName();
    }

    public String getLeftName() {
        return leftName;
    }

    public String getRightName() {
        return rightName;
    }

    /**
     * Ejecuta el combate completo. Bloquea el hilo que lo llama (con pausas entre turnos),
     * por lo que debe invocarse fuera del Event Dispatch Thread.
     */
    public void fight(BattleListener listener) throws InterruptedException {
        left.resetHp();
        right.resetHp();

        listener.onHpChanged(leftName, left.getCurrentHp());
        listener.onHpChanged(rightName, right.getCurrentHp());

        Pokemon attacker = chooseFirstTurn();
        Pokemon defender = attacker == left ? right : left;

        while (true) {
            Thread.sleep(TURN_DELAY_MS);

            AttackResult result = calculateDamage(attacker, defender);
            defender.applyDamage(result.damage);

            listener.onTurn(nameOf(attacker), nameOf(defender), result.damage, result.critical, result.modifier);
            listener.onHpChanged(nameOf(defender), defender.getCurrentHp());

            if (defender.isFainted()) {
                listener.onBattleEnded(nameOf(attacker));
                return;
            }

            Pokemon temp = attacker;
            attacker = defender;
            defender = temp;
        }
    }

    private String nameOf(Pokemon pokemon) {
        return pokemon == left ? leftName : rightName;
    }

    private Pokemon chooseFirstTurn() {
        if (left.getSpeed() > right.getSpeed()) {
            return left;
        }
        if (right.getSpeed() > left.getSpeed()) {
            return right;
        }
        return random.nextBoolean() ? left : right;
    }

    private AttackResult calculateDamage(Pokemon attacker, Pokemon defender) {
        double atkFactor = 0.85 + (random.nextDouble() * 0.30);
        double defFactor = 0.35 + (random.nextDouble() * 0.25);
        int baseDamage = (int) Math.round((attacker.getAttack() * atkFactor) - (defender.getDefense() * defFactor));
        baseDamage = Math.max(1, baseDamage);

        boolean critical = random.nextDouble() < CRITICAL_CHANCE;
        double critMultiplier = critical ? CRITICAL_MULTIPLIER : 1.0;
        double modifier = typeModifier(attacker.getPrimaryType(), defender.getPrimaryType());

        int totalDamage = (int) Math.max(1, Math.round(baseDamage * critMultiplier * modifier));
        return new AttackResult(totalDamage, critical, modifier);
    }

    /** Efectividad simple usando solo el primer tipo de cada Pokémon. */
    private double typeModifier(String attackerType, String defenderType) {
        if (beats(attackerType, defenderType)) {
            return SUPER_EFFECTIVE;
        }
        if (beats(defenderType, attackerType)) {
            return NOT_EFFECTIVE;
        }
        return 1.0;
    }

    private boolean beats(String a, String b) {
        return ("water".equals(a) && "fire".equals(b))
                || ("fire".equals(a) && "grass".equals(b))
                || ("grass".equals(a) && "water".equals(b));
    }

    private static class AttackResult {
        private final int damage;
        private final boolean critical;
        private final double modifier;

        private AttackResult(int damage, boolean critical, double modifier) {
            this.damage = damage;
            this.critical = critical;
            this.modifier = modifier;
        }
    }
}
