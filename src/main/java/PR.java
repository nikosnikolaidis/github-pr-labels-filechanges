import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class PR {
    String GitURLAPI;
    String projectOwner;
    String projectName;
    String id;
    String previousSha;
    String mergedSha;
    List<String> label;
    ArrayList<String> changedFiles = new ArrayList<>();


    public PR(String GitURL, String id, String mergedSha, List<String> label) {
        this.projectName = GitURL.split("/")[GitURL.split("/").length-1].replace(".git","");
        this.projectOwner = GitURL.split("/")[GitURL.split("/").length-2];
        this.id = id;
        this.mergedSha = mergedSha;
        this.label = label;
        this.GitURLAPI = GitURL.replace("https://github.com/","https://api.github.com/repos/");

        //Get previous SHA
        findPreviousSha(mergedSha);
    }

    /**
     * Get modified .java files in one commit
     */
    public void getJavaCodeChanged() {
        try {
            Unirest.setTimeouts(0, 0);
            HttpResponse<String> response = Unirest.get(GitURLAPI+"/commits/" + mergedSha)
                    .header("Authorization", "Bearer "+Main.token)
                    .asString();

            int responsecode =response.getStatus();
            if (responsecode != 200) {
                System.err.println(responsecode);
                //break;
            } else {
                //get response
                //Scanner sc = new Scanner(url.openStream());
                Scanner sc = new Scanner(response.getRawBody());
                String inline = "";
                while (sc.hasNext()) {
                    inline += sc.nextLine();
                }
                sc.close();

                JSONParser parse = new JSONParser();
                JSONObject jsonObject = (JSONObject) parse.parse(inline);

                JSONArray jsonArr_files = (JSONArray) jsonObject.get("files");
                if (jsonArr_files.size() > 0) {
                    for (int k = 0; k < jsonArr_files.size(); k++) {
                        JSONObject jsonObj_2 = (JSONObject) jsonArr_files.get(k);
                        String status = jsonObj_2.get("status").toString();
                        String filename = jsonObj_2.get("filename").toString();
                        if(status.equals("modified") && filename.endsWith(".java")){
                            changedFiles.add(filename);
                        }
                    }
                }
            }
        } catch (ParseException e) {
            throw new RuntimeException(e);
        } catch (UnirestException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Find previous commit from the given one
     * @param mergedSha
     */
    private void findPreviousSha(String mergedSha) {
        if (Main.isWindows()) {
            try {
                Process proc = Runtime.getRuntime().exec("cmd /c cd " +System.getProperty("user.dir")+ "\\" + projectName +
                        " && git rev-list --parents -n 1 " + mergedSha + "");
                BufferedReader inputReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                String inputLine;
                while ((inputLine = inputReader.readLine()) != null) {
                    System.out.println(inputLine);
                    String prSha = inputLine.replace(mergedSha + " ", "");
                    if (prSha.contains(" ")){
                        previousSha = prSha.split(" ")[prSha.split(" ").length-1];
                    }
                    else {
                        previousSha = inputLine.replace(mergedSha + " ", "");
                    }
                }
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
                String errorLine;
                while ((errorLine = errorReader.readLine()) != null) {
                    System.out.println(errorLine);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else{
            try {
                ProcessBuilder pbuilder = new ProcessBuilder("bash", "-c",
                        "cd '" + System.getProperty("user.dir") +"/"+ projectName+"' ; git rev-list --parents -n 1 " +mergedSha);
                File err = new File("err.txt");
                pbuilder.redirectError(err);
                Process p = pbuilder.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                    String prSha = line.replace(mergedSha + " ", "");
                    if (prSha.contains(" ")){
                        previousSha = prSha.split(" ")[prSha.split(" ").length-1];
                    }
                    else {
                        previousSha = line.replace(mergedSha + " ", "");
                    }
                }
                BufferedReader reader_2 = new BufferedReader(new InputStreamReader(p.getErrorStream()));
                String line_2;
                while ((line_2 = reader_2.readLine()) != null) {
                    System.out.println(line_2);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public String getProjectName() {
        return projectName;
    }

    public String getId() {
        return id;
    }

    public String getProjectOwner() {
        return projectOwner;
    }

    public String getPreviousSha() {
        return previousSha;
    }

    public void setPreviousSha(String previousSha) {
        this.previousSha = previousSha;
    }

    public String getMergedSha() {
        return mergedSha;
    }

    public void setMergedSha(String mergedSha) {
        this.mergedSha = mergedSha;
    }

    public List<String> getLabel() {
        return label;
    }

    public void setLabel(List<String> label) {
        this.label = label;
    }

    public ArrayList<String> getChangedFiles() {
        return changedFiles;
    }

    @Override
    public String toString() {
        return "PR{" +
                "projectName='" + projectName + '\'' +
                ", id='" + id + '\'' +
                ", label=" + label + '\'' +
                ", changedFiles="+ changedFiles +
                '}';
    }

}
