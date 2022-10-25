import org.apache.commons.io.FileUtils;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@ApplicationScoped
public class ProcessorService {

    Logger log = Logger.getLogger(ProcessorService.class.getName());

    @Inject
    MessageProducer messageProducer;

    public List<String> findFilesToProcess(String root_path, String TYPE_FILE, Integer MAX_DOCKER, boolean getInfo) {
        //all files waiting to be process
        List<String> filesToProcess = Arrays.asList(Objects.requireNonNull(new File(root_path).list())).stream().filter(fp -> fp.contains(TYPE_FILE)).collect(Collectors.toList());
        log.info("Files to process: " + filesToProcess.toString());

        //all files to lowwerCase
        for (String fileToProcess: filesToProcess) {
            String fileName = fileToProcess.split("\\.")[0];
            new File(root_path + fileToProcess).renameTo(new File(root_path + fileToProcess.toLowerCase()));
            //Also rename mainFunction
            try {
                Path path = Paths.get(root_path + fileToProcess);
                Charset charset = StandardCharsets.UTF_8;

                String content = Files.readString(path, charset);
                content = content.replaceAll(fileName, fileName.toLowerCase());
                Files.write(path, content.getBytes(charset));
            } catch (IOException e) {
                log.severe(e.getMessage());
            }
        }

        filesToProcess = Arrays.asList(Objects.requireNonNull(new File(root_path).list())).stream().filter(fp -> fp.contains(TYPE_FILE)).collect(Collectors.toList());
        log.info("Files to process: " + filesToProcess.toString());

        //CHEK if file already in process
        for (String fileToProcess: filesToProcess) {
            String folderName = fileToProcess.split("\\.")[0];
            String workingFolder = root_path + "\\" + folderName;
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command(
                    "docker",
                    "ps",
                    "-f",
                    "name="+folderName.toLowerCase(),
                    "--format",
                    "\"table {{.Names}}\"");
            String output = executeCommand(processBuilder, root_path, folderName, fileToProcess);
            String[] lines = output.split("\n");
            if (lines.length == 2) {
                //if yes, kill it and wait to new code to be run again
                processBuilder = new ProcessBuilder();
                processBuilder.command(
                        "docker",
                        "stop",
                        lines[1].toLowerCase());
                executeCommand(processBuilder, root_path, lines[1], fileToProcess);
                getLogs(lines[1], root_path);
                moveDependedFiles(root_path, lines[1], "01FAILED\\");
                cleanUP(root_path + lines[1]); /// TODO PREVERI ZAKAJ NEDELA CLEANUP
                removeDeadContainer(lines[1], root_path);
            }
            chekIfNewVersion(root_path, fileToProcess, 1);
        }

        filesToProcess = Arrays.asList(Objects.requireNonNull(new File(root_path).list())).stream().filter(fp -> fp.contains(TYPE_FILE)).collect(Collectors.toList());
        log.info("Files to process: " + filesToProcess.toString());



        //CHEK number od running dockers
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(
                "docker",
                "ps");

        String output = executeCommand(processBuilder, root_path, null, null);

        //for info endpoint
        if (getInfo) {
        String[] infoLines = output.split("\n");
        List<String> tmp = Arrays.asList(infoLines);
        List<String> info = new ArrayList<>();
        info.add("Currently running: " + tmp);
        info.add("Wating list:");
        info.addAll(filesToProcess);
            return info;
        }

        //get maximum files to process
        int numberOfRunningDockers = output.length() - output.replaceAll("b-","!").length();
        int numberOfNewFiles = filesToProcess.size();
        int numberOfNewAllowedFiles = MAX_DOCKER - numberOfRunningDockers;
        if (numberOfNewFiles > numberOfNewAllowedFiles) {
            List<String> allowedFilesToProcess = new ArrayList<>();
            for (int i = 0; i < numberOfNewAllowedFiles; i++) {
                allowedFilesToProcess.add(filesToProcess.get(i));
            }
            allowedFilesToProcess.forEach(f -> messageProducer.sendMessage(f, Status.TO_BE_PROCESSED));
            return allowedFilesToProcess;
        } else {
            filesToProcess.forEach(f -> messageProducer.sendMessage(f, Status.TO_BE_PROCESSED));
            return filesToProcess;
        }
    }

    private void chekIfNewVersion(String root_path, String fileToProcess, int i) {
        boolean check1;
        boolean check2;

        String folderName = fileToProcess.split("\\.")[0];
        String fileType = fileToProcess.split("\\.")[1];

        String newFileName = folderName + "_v" + i + "." + fileType;

        if (i == 1) {
            check1 = new File(root_path + "01FAILED\\" + fileToProcess).exists();
            check2 = new File(root_path + "02SUCCESS\\" + fileToProcess).exists();
        } else {
            check1 = new File(root_path + "01FAILED\\" + newFileName).exists();
            check2 = new File(root_path + "02SUCCESS\\" + newFileName).exists();
        }

        if (check1 || check2) {
            chekIfNewVersion(root_path, fileToProcess, i + 1);
        } else {
            if (i != 1) {
                new File(root_path + fileToProcess).renameTo(new File(root_path + newFileName));
                //Also rename mainFunction
                try {
                    Path path = Paths.get(root_path + newFileName);
                    Charset charset = StandardCharsets.UTF_8;

                    String content = Files.readString(path, charset);
                    content = content.replaceAll(folderName, folderName + "_v" + i);
                    Files.write(path, content.getBytes(charset));
                } catch (IOException e) {
                    log.severe(e.getMessage());
                }
            }
        }
    }



    public void createOwnWorkingDirectory(String root_path, List<String> filesToProcess) throws IOException {
        List<String> failed = new ArrayList<>();
        for(String file : filesToProcess) {
            String folderName = file.split("\\.")[0];
            String workingFolder = root_path + "\\" + folderName;
            boolean success = new File(workingFolder).mkdirs();
            if (success) {
                boolean successMove = moveFile(root_path, file, workingFolder, file);
                if (!successMove){
                    FileUtils.deleteDirectory(new File(workingFolder));
                    failed.add(file);
                } else {
                    try {
                        File fileLog = new File(workingFolder + "\\" +folderName + "-log.txt");
                        fileLog.createNewFile();
                    } catch(Exception e) {
                        e.printStackTrace();
                    }
                }
            } else {
                log.info("Failed --- to create directory from: " + workingFolder);
            }
        }

        filesToProcess.removeAll(failed);

    }

    public void copyAllNeededFiles(String root_path, List<String> filesToProcess){
        for(String file : filesToProcess) {
            String folderName = file.split("\\.")[0];
            String workingFolder = root_path + "\\" + folderName;

            File source = new File(root_path + "\\00ROOT");
            File dest = new File(workingFolder);
            try {
                FileUtils.copyDirectory(source, dest);
            } catch (IOException e) {
                log.info("Failed --- Copied files from ROOT to " + workingFolder + " --- " +e.getMessage());
            }
            log.info("Copied files from root to " + file);
        }
    }

    public void startDocker(String root_path, List<String> filesToProcess) {
        for(String file : filesToProcess) {
           String folderName = file.split("\\.")[0];
           String workingFolder = root_path + folderName;

            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command(
                    "docker", "build",
                    "-t", "b-"+folderName.toLowerCase(),
                    "\""+workingFolder+"\"");

            String output = executeCommand(processBuilder, root_path, folderName, file);
            if (output!= null) {

                processBuilder = new ProcessBuilder();
                processBuilder.command(
                        "docker", "run",
                        "-d",
                        "-v", workingFolder+":/usr/src/myapp/out",
                        "--name", folderName.toLowerCase(),
                        "--network", "none",
                        "b-"+folderName.toLowerCase());
                executeCommand(processBuilder, root_path, folderName, file);
            }
        }
    }

    private String executeCommand1(ProcessBuilder processBuilder, String root_path, String folderName, String file, boolean logs) {
        String commandRequest = String.join(" ", processBuilder.command().toArray(new String[0]));
        log.info(commandRequest);
        try {

            Random rand = new Random();
            String errorFileName = "errorFile_" + rand.nextInt();                           //because threading
            File errorFile = new File(root_path + errorFileName + ".txt");
            Process process = processBuilder
                    .redirectError(errorFile)
                    .start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String text = "", lineHolder;
            while ((lineHolder = reader.readLine()) != null) {
                text = text + lineHolder + "\n";
                log.info(lineHolder);
            }
            int exitCode = process.waitFor();
            log.info("\nExited with error code : " + exitCode);

            if (exitCode != 0 && !file.equals("none")) {
                reverse(root_path, folderName, file);
            } else {
                appendErrorIfExsit(errorFile, root_path, commandRequest);

                //for some reason, docker container logs dockerName, return 0 and write to error file, BufferReader is empty
                if (logs) {
                    String logsText = getTextFromFile(errorFile);
                    errorFile.delete();
                    return logsText;
                }
                errorFile.delete();
                return text;
            }
        } catch (Exception e) {
            log.info("Failing executing terminal Commands:" + e.getMessage());
        }
        return null;
    }

    //TODO: CHEK WAHT IF THERE IS NO CONTAINER FOR LOGS WHAT IS CODE?
    private String executeCommand(ProcessBuilder processBuilder, String root_path, String folderName, String file) {
        String commandRequest = String.join(" ", processBuilder.command().toArray(new String[0]));
        log.info(commandRequest);

        Random rand = new Random();
        String errorFileName = "errorFile_" + rand.nextInt();                           //because threading
        File errorFile = new File(root_path + errorFileName + ".txt");
        int exitCode = 0;
        String text = "";
        try {
            Process process = processBuilder.redirectError(errorFile).start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String lineHolder;
            while ((lineHolder = reader.readLine()) != null) {
                text = text + lineHolder + "\n";
                log.info(lineHolder);
            }
            exitCode = process.waitFor();
            log.info("\nExited with error code : " + exitCode);

        } catch (Exception e) {
            log.info("Failing executing terminal Commands:" + e.getMessage());
        }

        String logsErrorText = getTextFromFile(errorFile);
        errorFile.delete();
        String commandLogOut = root_path + "outCommandLog.txt";
        if (folderName != null) {
            writeToFile(logsErrorText, root_path + folderName + "\\" + folderName +"-log.txt");
        }
        writeToFile(commandRequest, commandLogOut);
        writeToFile(logsErrorText, commandLogOut);

        if (exitCode != 0) {
            moveDependedFiles(root_path, folderName, "01FAILED\\");
            reverse(root_path, folderName, file);
        }

        return text;
    }


    private void appendErrorIfExsit(File errorFile, String root_path, String commandRequest) {

        String textFromFile = getTextFromFile(errorFile);
        String path = root_path + "outCommandLog.txt";
        writeToFile(commandRequest, path);
        writeToFile(textFromFile, path);
    }

    private void writeToFile(String text, String path) {
        File file = new File(path);
        PrintWriter out = null;
        try{
            out = new PrintWriter(new FileOutputStream(file, true));
        }catch (FileNotFoundException e) {
            log.info(e.getMessage());
            return;
        }
        if (text != null && !text.equals("")) {
            out.append(text + "\n");
        }
        out.close();
    }

    private String getTextFromFile(File errorFile) {
        try {
            Scanner myReader = new Scanner(errorFile);
            String all = "";
            while (myReader.hasNextLine()) {
                all = all + " " + myReader.nextLine();
            }
            myReader.close();
            return all;
        } catch (FileNotFoundException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
        return null;
    }

    //Automatically put file into 01FAILED and delete his working folder
    private void reverse(String root_path, String folderName, String file) {
        if (file == null) {
            return;
        }

        String workingFolder = root_path + folderName;
        boolean successMove = moveFile(workingFolder, file, root_path + "01FAILED\\", file);
        if (successMove){
            try {
                File directory = new File(workingFolder);
                File[] files = directory.listFiles();
                if (files != null && files.length != 0){
                    for(File filee : files) {
                        System.out.println(filee + " deleted.");
                        filee.delete();
                    }
                }
                if(directory.delete()) {
                    log.info("Directory Deleted: " + file);
                }
                else {
                    log.info("Directory not Found");
                }

            } catch (Exception e) {
                log.info("Failed deliting files" + e.getMessage());
            }
        } else {
            log.info("Failed to move file to FAILED: " + folderName);
        }
    }

    public void creteDockerFile(String root_path, List<String> filesToProcess) {
        for(String file : filesToProcess) {
            String folderName = file.split("\\.")[0];
            String workingFolder = root_path + folderName;

            String txtDockerfile =
                    "FROM openjdk:11\n" +
                            "COPY . /usr/src/myapp\n" +
                            "WORKDIR /usr/src/myapp\n" +
                            "RUN javac "+file+"\n" +
                            "CMD [\"java\", \""+folderName+"\"]";

            try {
                FileWriter myWriter = new FileWriter(workingFolder + "\\Dockerfile");
                myWriter.write(txtDockerfile);
                myWriter.close();
            } catch (IOException e) {
                try {
                    FileWriter myWriter = new FileWriter(root_path+"01FAILED\\"+folderName+".txt");
                    myWriter.write("failed to create Dockerfile");
                    myWriter.close();
                } catch (IOException e1) {
                    log.info(e1.getMessage());
                }
                reverse(root_path, folderName, file);
                log.info("Dockerfile creation failed: " + e.getMessage());
            }
        }

    }

    public void chekEnding(String root_path) {
        ArrayList<String> directories = getAllDirectories(root_path);
        directories.remove("00ROOT");
        directories.remove("01FAILED");
        directories.remove("02SUCCESS");

        for(String directory : directories) {
            String workingFolder = root_path + directory;
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command(
                    "docker",
                    "ps",
                    "-f",
                    "name="+directory.toLowerCase());
            String output = executeCommand(processBuilder, root_path, directory, directory + ".java");
            String[] lines = output.split("\n");
            if (lines.length == 2) {
                //chek if container running to long
                processBuilder = new ProcessBuilder();
                processBuilder.command(
                        "docker",
                        "ps",
                        "--filter",
                        "name="+directory.toLowerCase(),
                        "--format",
                        "\"table {{.Status}}\"");
                output = executeCommand(processBuilder, root_path, directory, directory + ".java");
                if (output.contains("hours")) {
                    getLogs(directory, root_path);
                    done(root_path,  directory,true);
                    removeDeadContainer(directory, root_path);
                    messageProducer.sendMessage(directory, Status.FAILED);
                }
            }else {
                getLogs(directory, root_path);
                done(root_path,  directory,false);
                removeDeadContainer(directory, root_path);
                messageProducer.sendMessage(directory, Status.DONE);
            }
        }
    }

    private void removeDeadContainer(String directory, String root_path) {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(
                "docker",
                "rm",
                "-f",
                directory.toLowerCase());
        executeCommand(processBuilder, root_path, directory, directory + ".java");
    }

    private void done(String root_path, String directory, boolean timeout) {
        String workingFolder = root_path + directory;

        boolean haveOutput = Arrays.stream(Objects.requireNonNull(new File(workingFolder).list())).anyMatch(fp -> fp.contains("out.txt"));

        if (timeout) {
            writeToFile("----- TIMEOUT -------", root_path + directory + "\\" +directory +"-log.txt");
            moveDependedFiles(root_path, directory, "01FAILED\\");
        } else {
            if (haveOutput) {
                moveDependedFiles(root_path, directory, "02SUCCESS\\");
            }else {
                writeToFile("----- NO OUTPUT FILE -------", root_path + directory + "\\" +directory + "-log.txt");
                moveDependedFiles(root_path, directory, "01FAILED\\");
            }
        }
        cleanUP(workingFolder);

    }

    //This function moves out files and log files of container to 01FAILED or 02SUCCESS
    private boolean moveDependedFiles(String root_path, String directory, String status) {
        String workingFolder = root_path + directory;
        boolean successMove = moveFile(workingFolder, directory +".java", root_path + status, directory +".java");
        boolean successMove1 = moveFile(workingFolder, "out.txt", root_path + status, directory +".txt");
        boolean successMove3 = moveFile(workingFolder, directory +"-log.txt", root_path + status, directory +"-log.txt");

        return successMove && successMove1 && successMove3;
    }

    private void getLogs(String directory, String root_path) {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(
                "docker",
                "container",
                "logs",
                directory.toLowerCase());
        executeCommand(processBuilder, root_path, directory, directory + ".java");
    }

    private Boolean moveFile(String oldDir, String oldName, String newDir, String newName) {
        File current = new File(oldDir + "\\" + oldName);
        if (!current.exists()) {
            return true; // its okay for us if file dont exist
        }

        boolean successMove = current.renameTo(new File(newDir + "\\" + newName));
        if (!successMove){
            log.info("Failed --- : " + oldDir + "\\" + oldName + " to " + newDir + "\\" + newName);
        }
        return successMove;
    }

    private void cleanUP(String workingFolder) {
        try {
            File directory1 = new File(workingFolder);
            File[] files = directory1.listFiles();
            if (files != null && files.length != 0){
                for(File filee : files) {
                    System.out.println(filee + " deleted.");
                    filee.delete();
                }
            }
            if(directory1.delete()) {
                log.info("Directory Deleted: " + directory1);
            }
            else {
                log.info("Directory not Found");
            }

        } catch (Exception e) {
            log.info("Failed deliting files" + e.getMessage());
        }
    }

    ArrayList<String> getAllDirectories(String path) {
        File file = new File(path);
        String[] directories = file.list(new FilenameFilter() {
            @Override
            public boolean accept(File current, String name) {
                return new File(current, name).isDirectory();
            }
        });
        return new ArrayList<String>(Arrays.asList(directories));
    }

}
