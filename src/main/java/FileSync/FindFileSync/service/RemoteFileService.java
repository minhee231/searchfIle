package FileSync.FindFileSync.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.io.FileSystemResource;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Service
public class RemoteFileService {

    @Value("${config.serverUrl}")
    private String serverUrl;

    @Value("${config.sourceDir}")
    private String sourceDir;

    private static String UPLOAD_URL;
    private static String DELETE_URL;
    private static String FILE_EXISTS_URL;
    private static String UPDATE_DIR_URL;
    private static String DELETE_DIR_URL;


    @PostConstruct
    public void init() {
        //UPLOAD_URL = "http://" + serverUrl + "/api/files/upload";
        UPLOAD_URL = "http://" + serverUrl + "/file/upload";
        DELETE_URL = "http://" + serverUrl + "/file/delete";
        FILE_EXISTS_URL = "http://" + serverUrl + "/file/exists";
        UPDATE_DIR_URL = "http://" + serverUrl + "/file/update/dir";
        DELETE_DIR_URL = "http://" + serverUrl + "/file/deleteDirectory";


        log.info(FILE_EXISTS_URL);
    }

//    private static final String UPLOAD_URL = "http:///api/files/upload";
//    private static final String DELETE_URL = "http://localhost:8081/api/files/delete";
//    private static final String FILE_EXISTS_URL = "http://localhost:8081/api/files/exists";


    private final RestTemplate restTemplate = new RestTemplate();

    // ScheduledExecutorService를 사용하여 디바운스 구현
    //private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    //private final Map<String, ScheduledFuture<?>> debounceMap = new ConcurrentHashMap<>();

    // 최신 요청만 실행하기 위해 사용할 디바운스 대기 시간 (밀리초)
    // private static final int DEBOUNCE_DELAY_MILLIS = 0;

    public void uploadDir(Path path) {
        String filePath = path.toString();
        try {
            // 요청 body 생`성
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = Map.of("path", filePath.substring(sourceDir.length()));
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(body, headers);

            // Update 요청 전송
            ResponseEntity<String> response = restTemplate.exchange(UPDATE_DIR_URL, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                System.out.println("디렉토리 업데이트 성공: " + filePath);
            } else {
                System.out.println("디렉토리 업데이트 실패: " + response.getBody());
            }

        } catch (Exception e) {
            System.err.println("디렉토리 업데이트 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 파일 업로드 요청
    public void uploadFile(Path localFilePath, String fileName) {
        try {
            String fileKey = localFilePath.toString(); // 파일의 경로를 Key로 사용
            log.info("Uploading file " + fileName);
            //log.info(fileKey);
            // 기존 예약 작업이 있다면 취소
            /*if (debounceMap.containsKey(fileKey)) {
                debounceMap.get(fileKey).cancel(false);
            }*/

            // 새 예약 작업 등록
            //ScheduledFuture<?> future = scheduler.schedule(() -> {
                try {
                    File file = localFilePath.toFile();
                    if (!file.exists()) {
                        throw new FileNotFoundException("파일이 존재하지 않습니다: " + localFilePath);
                    }
                    //FileSystemResource fileResource = new FileSystemResource(localFilePath.toFile());
                    FileSystemResource fileResource = getFileResource(localFilePath);

                    // 요청 body 생성
                    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                    body.add("file", fileResource);
                    body.set("path", fileResource.getPath().substring(sourceDir.length()));

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.MULTIPART_FORM_DATA); // 반드시 MULTIPART_FORM_DATA 설정

                    HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);


                    ResponseEntity<String> response = restTemplate.postForEntity(UPLOAD_URL, requestEntity, String.class);


                    if (response.getStatusCode() == HttpStatus.OK) {
                        System.out.println("파일 업로드 성공: " + localFilePath);
                    } else {
                        System.out.println("파일 업로드 실패: " + response.getStatusCode());
                    }
                } catch (Exception e) {
                    System.err.println("파일 업로드 중 오류 발생: " + e.getMessage());
                    e.printStackTrace();
                }
           // });

            // 예약 작업 등록
          //  debounceMap.put(fileKey, future);
        } catch (Exception e) {
            System.err.println("파일 업로드 요청 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 파일 삭제 요청
    public void deleteFile(String filePath) {
        try {
            // 요청 body 생성
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String path = filePath.substring(sourceDir.length());

            Map<String, String> body = Map.of("path", path);
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(body, headers);

            // DELETE 요청 전송
            //ResponseEntity<String> response = restTemplate.exchange(DELETE_URL, HttpMethod.DELETE, requestEntity, String.class);
            path = URLEncoder.encode(path, StandardCharsets.UTF_8);
            ResponseEntity<String> response = restTemplate.getForEntity(DELETE_URL + "?path=" + path, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                System.out.println("파일 삭제 성공: " + filePath);
            } else {
                log.info(filePath);
                System.out.println("파일 삭제 실패: " + response.getBody());
            }

        } catch (Exception e) {
            System.err.println("파일 삭제 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }
    public boolean fileExistsOnServer(String path) {
        path = path.substring(sourceDir.length());
        try {
            ResponseEntity<Boolean> response = restTemplate.getForEntity(FILE_EXISTS_URL + "?path=" + path, Boolean.class);
            return Boolean.TRUE.equals(response.getBody());
        } catch (Exception e) {
            System.err.println("파일 존재 여부 확인 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
            return false; // 서버 확인 실패 시 기본값으로 false 반환
        }
    }

    public void deleteDir(String path) {
        try {
            // 요청 body 생성
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = Map.of("path","\\" + path);
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(body, headers);

            // DELETE 요청 전송
            ResponseEntity<String> response = restTemplate.exchange(DELETE_DIR_URL, HttpMethod.DELETE, requestEntity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                System.out.println("디렉토리 삭제 성공: " + path);
            } else {
                System.out.println("디렉토리 삭제 실패: " + response.getBody());
            }

        } catch (Exception e) {
            System.err.println("디렉토리 삭제 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }

    }

    public FileSystemResource getFileResource(Path localFilePath) throws FileNotFoundException {
        if (!Files.exists(localFilePath) || !Files.isRegularFile(localFilePath)) {
            throw new FileNotFoundException("파일이 존재하지 않거나 올바른 파일이 아닙니다: " + localFilePath);
        }

        return new FileSystemResource(localFilePath.toFile());
    }

}

