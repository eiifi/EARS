import io.quarkus.scheduler.Scheduled;

import java.util.*;
import java.io.IOException;
import java.util.logging.Logger;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.smallrye.common.annotation.Blocking;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;

@Path("/files")
public class FileResource {

    Logger log = Logger.getLogger(FileResource.class.getName());
  //  @ConfigProperty(name = "root.path")
    protected String ROOT_PATH= "C:\\Users\\Uporabnik\\Desktop\\magistersko delo\\rest-client-multipart-quickstart\\src\\main\\resources\\processor\\";
  //  @ConfigProperty(name = "maximum.docker")
    protected Integer MAX_DOCKER = 3;
    @ConfigProperty(name = "file.type")
    protected String TYPE_FILE;

    @Inject
    FileService fileService;

    @Inject
    ProcessorService processorService;

    @POST
    @Consumes("multipart/form-data")
    public Response uploadFile(@MultipartForm DataStream dataStream) {
        fileService.saveDataStream(dataStream, ROOT_PATH, TYPE_FILE);
        return Response.accepted().build();
    }

    @Scheduled(every="180s")
    @Blocking
    public void doJob() throws IOException {
        List<String> filesToProcess = processorService.findFilesToProcess(ROOT_PATH, TYPE_FILE, MAX_DOCKER, false);
        processorService.createOwnWorkingDirectory(ROOT_PATH, filesToProcess);
        processorService.copyAllNeededFiles(ROOT_PATH, filesToProcess);
        processorService.creteDockerFile(ROOT_PATH, filesToProcess);
        processorService.startDocker(ROOT_PATH, filesToProcess);
        processorService.chekEnding(ROOT_PATH);
    }

    @GET
    public void someTestingShit() throws IOException {
        doJob();
    }

    @GET
    @Path("/status")
    @Produces(MediaType.TEXT_PLAIN)
    public List<String> status(){
        //return processorService.findFilesToProcess(ROOT_PATH, TYPE_FILE, MAX_DOCKER, true);
        return List.of("Currently running:", "markokuzner", "janikaukler", "Wating list:", "maticvipotnik.java");
    }


}