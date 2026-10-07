package stadium;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Cliente de solo lectura para https://pokeapi.co/api/v2/pokemon/{name}.
 * Las llamadas son bloqueantes: deben hacerse fuera del Event Dispatch Thread.
 */
public class PokeApiClient {
    private static final String API_BASE = "https://pokeapi.co/api/v2/pokemon/";
    private static final int MAX_POKEMON_ID = 1025;

    private final HttpClient client;
    private final Random random;

    public PokeApiClient() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.random = new Random();
    }

    public Pokemon fetchByName(String name) throws IOException, InterruptedException {
        if (name == null || name.trim().isEmpty()) {
            throw new IOException("Debes ingresar un nombre.");
        }
        String cleaned = name.trim().toLowerCase(Locale.ROOT);
        String encoded = URLEncoder.encode(cleaned, StandardCharsets.UTF_8);
        return fetch(encoded, cleaned);
    }

    public Pokemon fetchRandom() throws IOException, InterruptedException {
        int id = random.nextInt(MAX_POKEMON_ID) + 1;
        return fetch(String.valueOf(id), "ID " + id);
    }

    private Pokemon fetch(String identifier, String originalInput) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + identifier))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new IOException("Error de red: PokeAPI no respondió a tiempo.", e);
        } catch (IOException e) {
            throw new IOException("Error de red: no se pudo conectar con PokeAPI. Revisa tu conexión a internet.", e);
        }

        if (response.statusCode() == 404) {
            throw new IOException("Pokémon no encontrado: " + originalInput);
        }
        if (response.statusCode() != 200) {
            throw new IOException("Error de API. Código HTTP: " + response.statusCode());
        }

        try {
            return parsePokemon(new JSONObject(response.body()));
        } catch (JSONException e) {
            throw new IOException("Respuesta inválida de PokeAPI.", e);
        }
    }

    private Pokemon parsePokemon(JSONObject json) throws IOException {
        String name = json.getString("name");
        List<String> types = parseTypes(json.getJSONArray("types"));
        JSONArray stats = json.getJSONArray("stats");
        int hp = getBaseStat(stats, "hp");
        int attack = getBaseStat(stats, "attack");
        int defense = getBaseStat(stats, "defense");
        int speed = getBaseStat(stats, "speed");
        String sprite = parseSpriteUrl(json);

        return new Pokemon(name, sprite, types, hp, attack, defense, speed);
    }

    private List<String> parseTypes(JSONArray typesArray) {
        List<String> types = new ArrayList<>();
        for (int i = 0; i < typesArray.length(); i++) {
            JSONObject typeObj = typesArray.getJSONObject(i).getJSONObject("type");
            types.add(typeObj.getString("name"));
        }
        return types;
    }

    private int getBaseStat(JSONArray statsArray, String statName) throws IOException {
        for (int i = 0; i < statsArray.length(); i++) {
            JSONObject stat = statsArray.getJSONObject(i);
            String name = stat.getJSONObject("stat").getString("name");
            if (statName.equals(name)) {
                return stat.getInt("base_stat");
            }
        }
        throw new IOException("No se encontró el stat " + statName + " en la respuesta.");
    }

    /** Sprite frontal; si el Pokémon no lo tiene, se usa el artwork oficial. */
    private String parseSpriteUrl(JSONObject pokemonJson) {
        JSONObject sprites = pokemonJson.getJSONObject("sprites");
        String front = sprites.optString("front_default", "");
        if (!front.isBlank() && !"null".equals(front)) {
            return front;
        }
        JSONObject other = sprites.optJSONObject("other");
        JSONObject artwork = other == null ? null : other.optJSONObject("official-artwork");
        return artwork == null ? "" : artwork.optString("front_default", "");
    }
}
