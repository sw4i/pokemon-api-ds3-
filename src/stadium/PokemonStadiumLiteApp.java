package stadium;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.Callable;

/**
 * Ventana principal: selección de Pokémon, vista de stats y log de batalla.
 * Las peticiones de red y el combate corren en SwingWorker para no bloquear la UI,
 * y la UI del combate solo se actualiza a partir de los eventos de {@link BattleListener}.
 */
public class PokemonStadiumLiteApp extends JFrame implements BattleListener {
    private static final int SPRITE_SIZE = 150;

    private final PokeApiClient apiClient = new PokeApiClient();

    private final JTextField leftInput = new JTextField(12);
    private final JButton leftLoadButton = new JButton("Load");
    private final JButton leftRandomButton = new JButton("Random");
    private final JLabel leftSpriteLabel = new JLabel("Sin imagen", SwingConstants.CENTER);
    private final JLabel leftNameLabel = new JLabel("Nombre: -");
    private final JLabel leftTypesLabel = new JLabel("Tipos: -");
    private final JLabel leftStatsLabel = new JLabel("HP - | ATK - | DEF - | SPD -");
    private final JProgressBar leftHpBar = new JProgressBar();
    private final JLabel leftHpTextLabel = new JLabel("HP actual: -");

    private final JTextField rightInput = new JTextField(12);
    private final JButton rightLoadButton = new JButton("Load");
    private final JButton rightRandomButton = new JButton("Random");
    private final JLabel rightSpriteLabel = new JLabel("Sin imagen", SwingConstants.CENTER);
    private final JLabel rightNameLabel = new JLabel("Nombre: -");
    private final JLabel rightTypesLabel = new JLabel("Tipos: -");
    private final JLabel rightStatsLabel = new JLabel("HP - | ATK - | DEF - | SPD -");
    private final JProgressBar rightHpBar = new JProgressBar();
    private final JLabel rightHpTextLabel = new JLabel("HP actual: -");

    private final JButton fightButton = new JButton("Fight!");
    private final JTextArea battleLog = new JTextArea(12, 60);
    private final JLabel statusLabel = new JLabel("Cargar ambos Pokémon para comenzar.");

    private Pokemon leftPokemon;
    private Pokemon rightPokemon;
    private Battle currentBattle;
    private boolean battleRunning;
    private boolean loading;
    private int turnNumber;

    public PokemonStadiumLiteApp() {
        super("Pokémon Stadium Lite");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        setPreferredSize(new Dimension(980, 680));

        add(buildArenaPanel(), BorderLayout.CENTER);
        add(buildBottomPanel(), BorderLayout.SOUTH);
        registerListeners();

        fightButton.setEnabled(false);
        battleLog.setEditable(false);
        pack();
        setLocationRelativeTo(null);
    }

    private JPanel buildArenaPanel() {
        JPanel arena = new JPanel(new GridLayout(1, 2, 10, 10));
        arena.add(buildPokemonPanel(
                "Jugador 1", leftInput, leftLoadButton, leftRandomButton, leftSpriteLabel,
                leftNameLabel, leftTypesLabel, leftStatsLabel, leftHpBar, leftHpTextLabel));
        arena.add(buildPokemonPanel(
                "Jugador 2", rightInput, rightLoadButton, rightRandomButton, rightSpriteLabel,
                rightNameLabel, rightTypesLabel, rightStatsLabel, rightHpBar, rightHpTextLabel));
        return arena;
    }

    private JPanel buildPokemonPanel(
            String title,
            JTextField input,
            JButton loadButton,
            JButton randomButton,
            JLabel spriteLabel,
            JLabel nameLabel,
            JLabel typesLabel,
            JLabel statsLabel,
            JProgressBar hpBar,
            JLabel hpTextLabel) {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder(title));

        JPanel top = new JPanel();
        top.add(new JLabel("Nombre:"));
        top.add(input);
        top.add(loadButton);
        top.add(randomButton);

        spriteLabel.setPreferredSize(new Dimension(170, 170));

        JPanel info = new JPanel(new GridLayout(5, 1));
        hpBar.setStringPainted(true);
        info.add(nameLabel);
        info.add(typesLabel);
        info.add(statsLabel);
        info.add(hpBar);
        info.add(hpTextLabel);

        panel.add(top, BorderLayout.NORTH);
        panel.add(spriteLabel, BorderLayout.CENTER);
        panel.add(info, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildBottomPanel() {
        JPanel bottom = new JPanel(new BorderLayout(5, 5));

        JPanel controls = new JPanel();
        controls.add(fightButton);
        controls.add(statusLabel);

        JScrollPane scrollPane = new JScrollPane(battleLog);
        bottom.add(controls, BorderLayout.NORTH);
        bottom.add(scrollPane, BorderLayout.CENTER);
        return bottom;
    }

    private void registerListeners() {
        leftLoadButton.addActionListener(e -> {
            String name = leftInput.getText();
            loadPokemonAsync(true, "Consultando PokeAPI...", "cargó a", () -> apiClient.fetchByName(name));
        });
        rightLoadButton.addActionListener(e -> {
            String name = rightInput.getText();
            loadPokemonAsync(false, "Consultando PokeAPI...", "cargó a", () -> apiClient.fetchByName(name));
        });
        leftRandomButton.addActionListener(e ->
                loadPokemonAsync(true, "Cargando Pokémon aleatorio...", "obtuvo aleatorio:", apiClient::fetchRandom));
        rightRandomButton.addActionListener(e ->
                loadPokemonAsync(false, "Cargando Pokémon aleatorio...", "obtuvo aleatorio:", apiClient::fetchRandom));
        fightButton.addActionListener(e -> startBattleAsync());
        // Enter en el campo de texto equivale a pulsar "Load".
        leftInput.addActionListener(e -> leftLoadButton.doClick());
        rightInput.addActionListener(e -> rightLoadButton.doClick());
    }

    /** Descarga el Pokémon y su sprite en segundo plano y luego actualiza la UI en el EDT. */
    private void loadPokemonAsync(boolean leftSide, String statusMessage, String logVerb, Callable<Pokemon> fetcher) {
        setLoadingState(true, statusMessage);
        SwingWorker<LoadedPokemon, Void> worker = new SwingWorker<LoadedPokemon, Void>() {
            @Override
            protected LoadedPokemon doInBackground() throws Exception {
                Pokemon pokemon = fetcher.call();
                ImageIcon icon = loadSpriteIcon(pokemon.getSpriteUrl());
                return new LoadedPokemon(pokemon, icon);
            }

            @Override
            protected void done() {
                setLoadingState(false, "Listo.");
                try {
                    LoadedPokemon loaded = get();
                    applyLoadedPokemon(leftSide, loaded);
                    battleLog.append((leftSide ? "J1" : "J2") + " " + logVerb + " " + loaded.pokemon.getName() + ".\n");
                } catch (Exception ex) {
                    showError(ex);
                }
            }
        };
        worker.execute();
    }

    private void applyLoadedPokemon(boolean leftSide, LoadedPokemon loaded) {
        Pokemon pokemon = loaded.pokemon;
        if (leftSide) {
            leftPokemon = pokemon;
            leftNameLabel.setText("Nombre: " + pokemon.getName());
            leftTypesLabel.setText("Tipos: " + String.join(", ", pokemon.getTypes()));
            leftStatsLabel.setText(formatStats(pokemon));
            leftSpriteLabel.setIcon(loaded.icon);
            leftSpriteLabel.setText(loaded.icon == null ? "Sin imagen" : "");
            setHp(leftHpBar, leftHpTextLabel, pokemon.getMaxHp(), pokemon.getCurrentHp());
            leftInput.setText(pokemon.getName());
        } else {
            rightPokemon = pokemon;
            rightNameLabel.setText("Nombre: " + pokemon.getName());
            rightTypesLabel.setText("Tipos: " + String.join(", ", pokemon.getTypes()));
            rightStatsLabel.setText(formatStats(pokemon));
            rightSpriteLabel.setIcon(loaded.icon);
            rightSpriteLabel.setText(loaded.icon == null ? "Sin imagen" : "");
            setHp(rightHpBar, rightHpTextLabel, pokemon.getMaxHp(), pokemon.getCurrentHp());
            rightInput.setText(pokemon.getName());
        }
        updateFightButtonState();
    }

    private String formatStats(Pokemon pokemon) {
        return String.format(
                Locale.ROOT,
                "HP %d | ATK %d | DEF %d | SPD %d",
                pokemon.getMaxHp(),
                pokemon.getAttack(),
                pokemon.getDefense(),
                pokemon.getSpeed());
    }

    private void startBattleAsync() {
        if (leftPokemon == null || rightPokemon == null || battleRunning || loading) {
            return;
        }

        Battle battle = new Battle(leftPokemon, rightPokemon);
        currentBattle = battle;
        battleRunning = true;
        turnNumber = 0;
        toggleControls(false);
        fightButton.setEnabled(false);
        battleLog.append("\n=== ¡Empieza el combate! " + battle.getLeftName() + " vs " + battle.getRightName() + " ===\n");
        statusLabel.setText("Combate en progreso...");

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                battle.fight(PokemonStadiumLiteApp.this);
                return null;
            }

            @Override
            protected void done() {
                battleRunning = false;
                toggleControls(true);
                updateFightButtonState();
                try {
                    get();
                } catch (Exception ex) {
                    showError(ex);
                }
            }
        };
        worker.execute();
    }

    private void setLoadingState(boolean loading, String message) {
        this.loading = loading;
        statusLabel.setText(message);
        if (!battleRunning) {
            toggleControls(!loading);
        }
        updateFightButtonState();
    }

    private void toggleControls(boolean enabled) {
        leftLoadButton.setEnabled(enabled);
        rightLoadButton.setEnabled(enabled);
        leftRandomButton.setEnabled(enabled);
        rightRandomButton.setEnabled(enabled);
    }

    private void updateFightButtonState() {
        fightButton.setEnabled(!battleRunning && !loading && leftPokemon != null && rightPokemon != null);
    }

    private void showError(Exception ex) {
        Throwable cause = ex.getCause() == null ? ex : ex.getCause();
        String message = cause.getMessage() == null ? "Error inesperado." : cause.getMessage();
        statusLabel.setText("Error.");
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void setHp(JProgressBar bar, JLabel textLabel, int maxHp, int currentHp) {
        bar.setMaximum(maxHp);
        bar.setValue(currentHp);
        bar.setString(currentHp + "/" + maxHp);
        textLabel.setText("HP actual: " + currentHp);
    }

    private ImageIcon loadSpriteIcon(String spriteUrl) {
        if (spriteUrl == null || spriteUrl.isBlank()) {
            return null;
        }
        try {
            BufferedImage original = ImageIO.read(new URL(spriteUrl));
            if (original == null) {
                return null;
            }
            // SCALE_REPLICATE mantiene nítido el pixel art del sprite al agrandarlo.
            Image scaled = original.getScaledInstance(SPRITE_SIZE, SPRITE_SIZE, Image.SCALE_REPLICATE);
            return new ImageIcon(scaled);
        } catch (IOException e) {
            return null;
        }
    }

    // Los eventos de BattleListener llegan desde el hilo del combate; por eso se
    // reenvían al EDT con invokeLater antes de tocar cualquier componente Swing.

    @Override
    public void onTurn(String attacker, String defender, int damage, boolean critical, double modifier) {
        SwingUtilities.invokeLater(() -> {
            turnNumber++;
            StringBuilder line = new StringBuilder();
            line.append("[Turno ").append(turnNumber).append("] ")
                    .append(attacker).append(" ataca a ").append(defender)
                    .append(" y hace ").append(damage).append(" de daño");
            if (critical) {
                line.append(" ¡CRÍTICO! (x1.5)");
            }
            if (modifier > 1.0) {
                line.append(String.format(Locale.ROOT, " ¡Es muy eficaz! (x%.1f)", modifier));
            } else if (modifier < 1.0) {
                line.append(String.format(Locale.ROOT, " No es muy eficaz... (x%.1f)", modifier));
            }
            battleLog.append(line + "\n");
            battleLog.setCaretPosition(battleLog.getDocument().getLength());
        });
    }

    @Override
    public void onHpChanged(String pokemon, int hpActual) {
        SwingUtilities.invokeLater(() -> {
            if (currentBattle == null) {
                return;
            }
            Pokemon target;
            if (currentBattle.getLeftName().equals(pokemon)) {
                target = leftPokemon;
                setHp(leftHpBar, leftHpTextLabel, target.getMaxHp(), hpActual);
            } else if (currentBattle.getRightName().equals(pokemon)) {
                target = rightPokemon;
                setHp(rightHpBar, rightHpTextLabel, target.getMaxHp(), hpActual);
            } else {
                return;
            }
            battleLog.append("    " + pokemon + ": HP restante " + hpActual + "/" + target.getMaxHp() + "\n");
            battleLog.setCaretPosition(battleLog.getDocument().getLength());
        });
    }

    @Override
    public void onBattleEnded(String winner) {
        SwingUtilities.invokeLater(() -> {
            battleLog.append("¡" + winner + " gana el combate!\n");
            battleLog.append("=== Fin del combate ===\n");
            battleLog.setCaretPosition(battleLog.getDocument().getLength());
            statusLabel.setText("Ganador: " + winner);
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PokemonStadiumLiteApp().setVisible(true));
    }

    private static class LoadedPokemon {
        private final Pokemon pokemon;
        private final ImageIcon icon;

        private LoadedPokemon(Pokemon pokemon, ImageIcon icon) {
            this.pokemon = pokemon;
            this.icon = icon;
        }
    }
}
