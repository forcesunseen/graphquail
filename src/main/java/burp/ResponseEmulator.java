package burp;
import java.io.*;

public class ResponseEmulator {

    private static String graphiqlBody;
    private static String voyagerBody;

    public String getGraphiql() {
        return graphiqlBody;
    }

    public String getVoyager() {
        return voyagerBody;
    }

    private String readAllLines(BufferedReader reader) throws IOException {
        StringBuilder content = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            content.append(line);
            content.append(System.lineSeparator());
        }

        return content.toString();
    }

    public ResponseEmulator() {
        try {
            InputStream graphiqlStream = getClass().getClassLoader().getResourceAsStream("graphiql.html");
            assert graphiqlStream != null;
            BufferedReader reader = new BufferedReader(new InputStreamReader(graphiqlStream));
            graphiqlBody = readAllLines(reader);
        }
        catch(Exception e) {
            assert true;
        }

        try {
            InputStream voyagerStream = getClass().getClassLoader().getResourceAsStream("voyager.html");
            assert voyagerStream != null;
            BufferedReader reader = new BufferedReader(new InputStreamReader(voyagerStream));
            voyagerBody = readAllLines(reader);
        }
        catch(Exception e) {
            assert true;
        }
    }
}
