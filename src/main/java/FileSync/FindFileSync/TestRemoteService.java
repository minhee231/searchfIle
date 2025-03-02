package FileSync.FindFileSync;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestOperations;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Path;

@Slf4j
@Service
public class TestRemoteService {

    @Value("${config.serverUrl}")
    private String serverUrl;

    private static String UPLOAD_URL;
    private static String DELETE_URL;
    private static String FILE_EXISTS_URL;

    @PostConstruct
    public void init() {
        //UPLOAD_URL = "http://" + serverUrl + "/api/files/upload";
        UPLOAD_URL = "http://" + serverUrl + "/file/upload";
        DELETE_URL = "http://" + serverUrl + "/api/files/delete";
        FILE_EXISTS_URL = "http://" + serverUrl + "/api/files/exists";

        log.info(FILE_EXISTS_URL);
    }

    public void test(Path localFilePath, String fileName) {

        FileSystemResource fileResource = new FileSystemResource(localFilePath.toFile());

        // 요청 body 생성
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileResource);
        body.add("relativePath", fileName);

        // 요청 헤더 생성
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // POST 요청 전송
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.postForEntity(UPLOAD_URL, requestEntity, String.class);

    }
}
