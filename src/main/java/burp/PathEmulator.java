package burp;

import static burp.Utils.generateIdentifier;

class PathEmulator {
    
    private String graphiqlPath;
    private String voyagerPath;
    private Boolean graphiqlEnabled;
    private Boolean voyagerEnabled;
    
    public String getGraphiqlPath() {

        return graphiqlPath;
    }
    
    public String getVoyagerPath() {

        return voyagerPath;
    }
    
    public Boolean graphiqlEnabled() {

        return graphiqlEnabled;
    }
    
    public Boolean voyagerEnabled() {

        return voyagerEnabled;
    }
    
    public void setGraphiqlState(Boolean state) {

        graphiqlEnabled = state;
    }
    
    public void setVoyagerState(Boolean state) {
        voyagerEnabled = state;
    }
    
    public Boolean setGraphiqlPath(String path) {

        if (!path.equals(voyagerPath)) {

            graphiqlPath = path;
            return true;
        }

        return false;
    }
    
    public Boolean setVoyagerPath(String path) {

        if (!path.equals(graphiqlPath)) {

            voyagerPath = path;
            return true;
        }

        return false;

    }

    public PathEmulator() {
        graphiqlPath = generateIdentifier(6);
        voyagerPath = "voyager";
        graphiqlEnabled = false;
        voyagerEnabled = false;
    }
}
