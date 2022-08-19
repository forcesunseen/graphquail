package burp;

import com.google.gson.JsonArray;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Random;

public class Utils {

    public static String generateIdentifier(int length){
        String chars = "abcdefghijkmnopqrstuvwxyz";
        Random random = new Random();
        StringBuilder token = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            token.append(chars.charAt(random.nextInt(chars.length())));
        }
        return token.toString();
    }

    public static String jsonQueryToRaw(String input) {
        String query;
        String variables;

        try {
            JSONObject parsed = new JSONObject(input);
            query = parsed.get("query").toString().strip();
            variables = parsed.get("variables").toString().strip();
        }
        catch(Exception a) {
            try {
                // If this succeeds, then the query is wrapped in a JSON array
                JSONArray parsedArray = new JSONArray(input);
                JSONObject parsed = (JSONObject) parsedArray.get(0);
                query = parsed.get("query").toString().strip();
                variables = parsed.get("variables").toString().strip();
            }
            catch(Exception b) {
                return "";
            }
        }
        return query + "\n\n#" + variables;
    }
}
