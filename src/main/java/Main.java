import java.io.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


public class Main {

    public static String GitURL="";
    public static String token="";
    public static List<String> givenLabels=new ArrayList<>();

    public static void main(String[] args) {

        //for convenience
        GitURL="https://github.com/apache/pulsar";
        givenLabels.add("type/refactor");
        token="";
        //for jar get from args
//        if(args.length==3){
//            GitURL=args[0];
//            givenLabels= Arrays.stream(args[1].split(";")).collect(Collectors.toList());
//            token=args[2];
//        }
//        else{
//            return;
//        }



        //Get api link and project name
        String GitURLAPI = GitURL.replace("https://github.com/","https://api.github.com/repos/");
        String projectName = GitURL.split("/")[GitURL.split("/").length-1];
        System.out.println(GitURLAPI);

        //clone project
        cloneGitProject();

        //create csv file
        try {
            FileWriter writer = new FileWriter(new File(System.getProperty("user.dir")+"/data_"+projectName+".csv"));
            writer.write("projectName;PR_id;previousSHA;mergedSHA;labels;file;" + System.lineSeparator());
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //init
        int page=1;
        boolean hasMore=true;
        ArrayList<PR> allPRs = new ArrayList<>();

        //start to get all PRs from API
        do{
            //for each page
            System.out.println();
            System.out.println("-------------------------------");
            System.out.println("-------------------------------");
            System.out.println("-------------------------------");
            System.out.println("Git API page: "+page);
            try {
                Unirest.setTimeouts(0, 0);
                HttpResponse<String> response = Unirest.get(GitURLAPI+"/pulls?state=closed&per_page=100&page="+page)
                        .header("Authorization", "Bearer "+Main.token)
                        .asString();

                int responsecode =response.getStatus();
                if (responsecode != 200) {
                    System.err.println(responsecode);
                } else {
                    //get response
                    Scanner sc = new Scanner(response.getRawBody());
                    String inline = "";
                    while (sc.hasNext()) {
                        inline += sc.nextLine();
                    }
                    sc.close();

                    //parse response
                    JSONParser parse = new JSONParser();
                    JSONArray jsonArr_1 = (JSONArray) parse.parse(inline);
                    for(int i=0; i<jsonArr_1.size(); i++) {
                        //for each PR
                        JSONObject jsonObj_1 = (JSONObject)jsonArr_1.get(i);
                        String id = jsonObj_1.get("url").toString();
                        //if it was merged
                        if(jsonObj_1.get("merge_commit_sha") != null) {
                            String mergedSHA = jsonObj_1.get("merge_commit_sha").toString();
                            JSONArray jsonArr_2 = (JSONArray) jsonObj_1.get("labels");
                            //if it has >0 labels
                            if (jsonArr_2.size() > 0) {
                                ArrayList<String> labels = new ArrayList<>();
                                for (int k = 0; k < jsonArr_2.size(); k++) {
                                    JSONObject jsonObj_2 = (JSONObject) jsonArr_2.get(k);
                                    String labelName = jsonObj_2.get("name").toString();
                                    labels.add(labelName);
                                }
                                //has label that is one of the given
                                boolean shouldContinue= true;
                                if(!givenLabels.isEmpty()) {
                                    shouldContinue = false;
                                    for (String s : labels) {
                                        if (givenLabels.contains(s)) {
                                            shouldContinue = true;
                                            break;
                                        }
                                    }
                                }
                                if (shouldContinue) {
                                    //create PR
                                    PR pr = new PR(GitURL, id, mergedSHA, labels);
                                    //if it has previous SHA
                                    if (pr.getPreviousSha() != null) {
                                        pr.getJavaCodeChanged();
                                        //if there are java files changed
                                        if(pr.getChangedFiles().size()>0){
                                            //write in csv each file changed
                                            FileWriter fw = new FileWriter(new File(System.getProperty("user.dir")+"/data_"+projectName+".csv"),true);
                                            for(String jf: pr.getChangedFiles()) {
                                                fw.write(pr.projectName + ";" + pr.id + ";" + pr.previousSha + ";" + pr.mergedSha + ";[" +
                                                        pr.label.stream().map(Object::toString).collect(Collectors.joining(",")).toString() + "];" +
                                                        jf + System.lineSeparator());
                                            }
                                            fw.close();
                                        }
                                        //Save this PR
                                        System.out.println("---------");
                                        System.out.println(pr);
                                        System.out.println("page: " + page + "     pr:" + (i + 1) + "/100");
                                        System.out.println("---------");
                                        allPRs.add(pr);
                                    }
                                }
                            }
                        }
                    }
                    if(jsonArr_1.size()==0){
                        hasMore=false;
                    }
                    page++;
                }
            } catch (IOException | ParseException e) {
                e.printStackTrace();
            } catch (UnirestException e) {
                throw new RuntimeException(e);
            }
        }while (hasMore);

    }

    /**
     * Clone the Project in user.dir
     * for windows and linux
     */
    public static void cloneGitProject() {
        if (isWindows()) {
            try {
                //Change dir and then clone
                Process proc = Runtime.getRuntime().exec("cmd /c cd " +System.getProperty("user.dir")+ " && "
                        + "git clone " + GitURL + "");
                BufferedReader inputReader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                String inputLine;
                while ((inputLine = inputReader.readLine()) != null) {
                    System.out.println(inputLine);
                }
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
                String errorLine;
                while ((errorLine = errorReader.readLine()) != null) {
                    System.out.println(errorLine);
                }
                System.out.println("Clone DONE!");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else {
            try {
                ProcessBuilder pbuilder = new ProcessBuilder("bash", "-c",
                        "cd '" + System.getProperty("user.dir") + "' ; git clone " + GitURL + "");
                File err = new File("err.txt");
                pbuilder.redirectError(err);
                Process p = pbuilder.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
                BufferedReader reader_2 = new BufferedReader(new InputStreamReader(p.getErrorStream()));
                String line_2;
                while ((line_2 = reader_2.readLine()) != null) {
                    System.out.println(line_2);
                }
                System.out.println("Clone DONE!");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Is Windows the OS
     * @return
     */
    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

}
