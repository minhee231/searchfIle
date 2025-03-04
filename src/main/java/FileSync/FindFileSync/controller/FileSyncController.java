package FileSync.FindFileSync.controller;

import FileSync.FindFileSync.service.RemoteFileService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileSyncController {

    @Value("${config.sourceDir}")
    private String sourceDir;

    @Value("#{'${config.allowedExtensions}'.split(',')}")
    private List<String> allowedExtensions;

    @Autowired
    private RemoteFileService remoteFileService;

    @PostConstruct
    public void searchfileApplication() throws IOException {
        try {
            log.info("최초 동기화를 수행 중...");
            Path sourcePath = Paths.get(sourceDir);

            // 최초 동기화: 기존 파일들을 확인하여 업로드
            Files.walkFileTree(sourcePath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (Files.isRegularFile(file) && isAllowedExtension(file)) {
                        String fileName = file.getFileName().toString();
                        CompletableFuture.runAsync(() -> uploadIfNotExists(file, file.toFile().getPath()));
                        log.info(file.toFile().getPath());
                        log.info(fileName);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            log.info("최초 동기화 완료!");

            // WatchService 설정
            WatchService watchService = FileSystems.getDefault().newWatchService();
            registerAllDirectories(sourcePath, watchService);

            // 파일 변화 감지
            Thread thread = new Thread(() -> {
                try {
                    while (true) {
                        WatchKey key;
                        try {
                            key = watchService.take();

                        } catch (InterruptedException e) {
                            log.error("와치 서비스 에러: ", e);
                            return;
                        }

                        for (WatchEvent<?> event : key.pollEvents()) {
                            WatchEvent.Kind<?> kind = event.kind();
                            Path fileName = (Path) event.context();
                            log.info(key.watchable().toString());
                            Path detectedFilePath = ((Path) key.watchable()).resolve(fileName);

                            log.info("감지됨: {} - {}", kind.name(), detectedFilePath);

                            // 디렉토리 감지 시, 새 경로로 업데이트
                            if (kind.equals(StandardWatchEventKinds.ENTRY_CREATE) && Files.isDirectory(detectedFilePath)) {
                                log.info("새 디렉토리 생성 감지됨: {}", detectedFilePath);
                                registerAllDirectories(detectedFilePath, watchService);
                                uploadFilesInDirectory(detectedFilePath);
                                continue;
                            }

                            // 폴더 이름 변경 감지
                            if (kind.equals(StandardWatchEventKinds.ENTRY_MODIFY) && Files.isDirectory(detectedFilePath)) {
                                log.info("디렉토리 변경 감지됨: {}", detectedFilePath);

                                // 기존 경로를 새로운 경로로 업데이트
                                Path parentDir = detectedFilePath.getParent();
                                if (parentDir != null) {
                                    //registerAllDirectories(parentDir, watchService);
                                    registerAllDirectories(parentDir, watchService);
                                }
                                continue;
                            }

                            // 확장자 체크
                            if (!isAllowedExtension(fileName)) {
                                log.info("확장자가 일치하지 않음(무시): {}", fileName);
                                continue;
                            }


                            // 파일 생성 시
                            if ((kind.equals(StandardWatchEventKinds.ENTRY_CREATE)) ||
                                    (kind.equals(StandardWatchEventKinds.ENTRY_MODIFY))) {
                                try {
                                    if (Files.isDirectory(detectedFilePath)) {
                                        remoteFileService.uploadDir(detectedFilePath);
                                    }
                                    log.info("업로드 처리: {}", detectedFilePath);

                                    // 업로드 실행
                                    remoteFileService.uploadFile(detectedFilePath, fileName.toString());
                                } catch (Exception e) {
                                    log.error("파일 업로드 요청 중 오류 발생: {}", detectedFilePath, e);
                                }
                            }


                            // 파일 삭제 시 삭제 처리
                            else if (kind.equals(StandardWatchEventKinds.ENTRY_DELETE)) {
                                try {
                                    remoteFileService.deleteFile(detectedFilePath.toString());
                                } catch (Exception e) {
                                    log.error("파일 삭제 요청 중 오류 발생: {}", fileName, e);
                                }
                            }
                        }

                        if (!key.reset()) {
                            log.warn("WatchKey could not be reset. Exiting...");
                            watchService.close();
                            break;
                        }
                    }
                } catch (IOException e) {
                    log.error("Error closing WatchService", e);
                }
            });

            thread.setDaemon(true);
            thread.start();

        } catch (IOException e) {
            log.error("Failed to initialize WatchService", e);
            throw e;
        }
    }

//    private void registerAllDirectories(Path start, WatchService watchService) throws IOException {
//        Files.walkFileTree(start, new SimpleFileVisitor<>() {
//            @Override
//            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
//                dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
//                        StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
//                log.info("디렉토리 감시 등록됨: {}", dir);
//                return FileVisitResult.CONTINUE;
//            }
//        });
//    }

    private Path registerAllDirectories(Path start, WatchService watchService) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_DELETE,
                        StandardWatchEventKinds.ENTRY_MODIFY);
                        remoteFileService.uploadDir(dir);
                return FileVisitResult.CONTINUE;
            }
        });
        return start; // 등록된 디렉토리의 Path 반환
    }



    private void uploadIfNotExists(Path entry, String path) {
        try {
            if (remoteFileService.fileExistsOnServer(path)) {
                log.info("파일이 이미 서버에 존재함, 업로드 생략: {}", path);
                return;
            }
            remoteFileService.uploadFile(entry, path);
            log.info("업로드 성공: {}", path);
        } catch (Exception e) {
            log.error("병렬 업로드 중 오류 발생: {}", path, e);
        }
    }

    // 확장자 필터링 메서드
    private boolean isAllowedExtension(Path file) {
        File f = file.toFile();

        // 파일이 존재하지 않지만 이름을 기준으로 디렉토리로 간주할 수 있음
        if (!f.exists() || f.isDirectory()) {
            log.info("디렉토리 감지: {}", file);
            return true;
        }

        String fileName = file.getFileName().toString();
        int lastDotIndex = fileName.lastIndexOf(".");

        // 확장자가 없는 경우 (디렉토리일 가능성이 높음)
        if (lastDotIndex == -1) {
            log.info("확장자가 없는 파일(무시): {}", fileName);
            return true; // 디렉토리로 간주
        }

        String fileExtension = fileName.substring(lastDotIndex + 1).toLowerCase().trim();
        log.info("파일명: {}, 추출된 확장자: {}", fileName, fileExtension);

        return allowedExtensions.contains(fileExtension);
    }

    private void uploadFilesInDirectory(Path directory) {
        try {
            Files.walk(directory) // 디렉토리 내 모든 파일과 서브디렉토리 순회
                    .filter(Files::isRegularFile) // 파일만 필터링
                    .filter(this::isAllowedExtension) // 확장자가 허용된 파일만 처리
                    .forEach(file -> {
                        try {
                            log.info("업로드 처리: {}", file);
                            remoteFileService.uploadFile(file, file.getFileName().toString()); // 파일 업로드
                        } catch (Exception e) {
                            log.error("파일 업로드 중 오류 발생: {}", file, e);
                        }
                    });
        } catch (IOException e) {
            log.error("디렉토리 내 파일 업로드 중 오류 발생: {}", directory, e);
        }
    }


}
