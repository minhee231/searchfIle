package FileSync.FindFileSync;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;


import java.io.IOException;
import java.nio.file.*;
import java.util.List;

@SpringBootApplication
public class MainApplication {
	public static void main(String[] args) {
		SpringApplication.run(MainApplication.class, args);
	}
}

@Slf4j
@Service
@RequiredArgsConstructor
class FileSyncController {

	@Value("${config.sourceDir}")
	private String sourceDir;

	@Value("#{'${config.allowedExtensions}'.split(',')}")
	private List<String> allowedExtensions;

	@Autowired
	private RemoteFileService remoteFileService;
	private WatchKey watchKey;

	//테스트
	@Autowired
	private TestRemoteService testRemoteService;

	@PostConstruct
	public void searchfileApplication() throws IOException {
		try {
			log.info("최초 동기화를 수행 중...");
			Path sourcePath = Paths.get(sourceDir);

			Files.walkFileTree(sourcePath, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					if (Files.isRegularFile(file) && isAllowedExtension(file)) {
						String fileName = file.getFileName().toString();
						CompletableFuture.runAsync(() -> uploadIfNotExists(file, fileName));
					}
					return FileVisitResult.CONTINUE;
				}
			});

			log.info("최초 동기화 완료!");

			WatchService watchService = FileSystems.getDefault().newWatchService();
			registerAllDirectories(sourcePath, watchService);

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
							Path detectedFilePath = ((Path) key.watchable()).resolve(fileName);
							Path sourceDirPath = Paths.get(sourceDir.replace("\\", "/"));
							detectedFilePath = sourceDirPath.relativize(detectedFilePath);

							log.info("감지됨: {} - {}", kind.name(), detectedFilePath);




							if (Files.isDirectory(detectedFilePath)) {
								// 새로운 디렉토리가 생기면 WatchService에 등록
								registerAllDirectories(detectedFilePath, watchService);
								continue;
							}

							if (!isAllowedExtension(fileName)) {
								log.info("확장자가 일치하지 않음(무시): {}", fileName);
								continue;
							}


							if (kind.equals(StandardWatchEventKinds.ENTRY_CREATE) ||
									kind.equals(StandardWatchEventKinds.ENTRY_MODIFY)) {
								try {
									log.info(String.valueOf(detectedFilePath));

									Path localFilePath = sourcePath.resolve(fileName);
//									remoteFileService.uploadFile(detectedFilePath, fileName.toString());
//									remoteFileService.uploadFile(localFilePath, fileName.toString());
									FileSystemResource fileResource = new FileSystemResource(localFilePath.toFile());
									//testRemoteService.test(fileResource, String.valueOf(fileName), localFilePath.toString() );
									testRemoteService.test(localFilePath, fileName.toString());

								} catch (Exception e) {
									log.error("파일 업로드 요청 중 오류 발생: {}", detectedFilePath, e);
								}
							} else if (kind.equals(StandardWatchEventKinds.ENTRY_DELETE)) {
								try {
									remoteFileService.deleteFile(fileName.toString());
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

	private void registerAllDirectories(Path start, WatchService watchService) throws IOException {
		Files.walkFileTree(start, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
						StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
				log.info("디렉토리 감시 등록됨: {}", dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private void uploadIfNotExists(Path entry, String fileName) {
		try {
			if (remoteFileService.fileExistsOnServer(fileName)) {
				log.info("파일이 이미 서버에 존재함, 업로드 생략: {}", fileName);
				return;
			}
			remoteFileService.uploadFile(entry, fileName);
			log.info("업로드 성공: {}", fileName);
		} catch (Exception e) {
			log.error("병렬 업로드 중 오류 발생: {}", fileName, e);
		}
	}


	// 파일 확장자 필터링 로직
//	private boolean isAllowedExtension(Path file) {
//		String fileName = file.toAbsolutePath().toString(); // 전체 경로를 가져옴
//		int lastDotIndex = fileName.lastIndexOf(".");
//		if (lastDotIndex == -1) {
//			return false; // 확장자가 없는 경우
//		}
//		// 파일 확장자를 소문자로 변환하여 필터링
//		String fileExtension = fileName.substring(lastDotIndex + 1).toLowerCase();
//		return allowedExtensions.contains(fileExtension);
//	}

	// 허용된 경로인지 확인하는 메서드 추가
	private boolean isAllowedPath(Path file) {
		try {
			Path absolutePath = file.toAbsolutePath().normalize(); // 절대 경로 변환
			Path rootPath = Paths.get(sourceDir).toAbsolutePath().normalize(); // 루트 디렉토리 절대 경로

			return absolutePath.getParent().equals(rootPath); // 부모 경로가 sourceDir과 동일한지 확인
		} catch (Exception e) {
			log.error("경로 검사 중 오류 발생: {}", file, e);
			return false;
		}
	}

	// 기존 확장자 검사 메서드 수정
	private boolean isAllowedExtension(Path file) {
//		if (!isAllowedPath(file)) {
//			log.info("파일이 허용된 루트 디렉토리에 없음(무시): {}", file);
//			return false;
//		}

		String fileName = file.getFileName().toString();
		int lastDotIndex = fileName.lastIndexOf(".");

		if (lastDotIndex == -1) {
			log.info("확장자가 없는 파일(무시): {}", fileName);
			return false;
		}

		String fileExtension = fileName.substring(lastDotIndex + 1).toLowerCase().trim();
		log.info("파일명: {}, 추출된 확장자: {}", fileName, fileExtension);


		return allowedExtensions.contains(fileExtension);
	}

}