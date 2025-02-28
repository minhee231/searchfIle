package FileSync.FindFileSync;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Service;

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

	private WatchKey watchKey;

	// 감지할 파일 확장자
//	private static final List<String> allowedExtensions = List.of("txt", "jpg", "pdf", "jpeg", "exe", "lnk", "zip", "avi", "mp4", "mkv", "mov", "ini");



	@PostConstruct
	public void searchfileApplication() throws IOException {
//		String sourceDir = "C:/MUCH/fileSync"; // 모니터링할 디렉토리
		/*String targetDir = "/Users/koo29/OneDrive/Desktop/FileBackup"; // 복사할 디렉토리(로컬 테스트)*/
		RemoteFileService remoteFileService = new RemoteFileService(); // RemoteFileService 인스턴스 생성

		// 1. 최초 실행 시 모든 파일 동기화
		try {
			log.info("최초 동기화를 수행 중...");
			Path sourcePath = Paths.get(sourceDir);

			try (DirectoryStream<Path> stream = Files.newDirectoryStream(sourcePath)) {
				stream.forEach(entry -> {
					if (Files.isRegularFile(entry) && isAllowedExtension(entry)) {
						String fileName = entry.getFileName().toString();

						CompletableFuture.runAsync(() -> {
							try {
								// 서버에 파일 존재 여부 확인
								if (remoteFileService.fileExistsOnServer(fileName)) {
									log.info("파일이 이미 서버에 존재함, 업로드 생략: {}", fileName);
									return; // 서버에 동일한 파일이 존재하면 업로드 생략
								}

								// 서버에 파일이 없으면 업로드
								remoteFileService.uploadFile(entry, fileName);
								log.info("업로드 성공: {}", fileName);
							} catch (Exception e) {
								log.error("병렬 업로드 중 오류 발생: {}", fileName, e);
							}
						});
					}
				});
			}


			/*try (DirectoryStream<Path> stream = Files.newDirectoryStream(sourcePath)) { // 원본 디렉토리 순회
				for (Path entry : stream) {
					if (Files.isRegularFile(entry) && isAllowedExtension(entry)) { // 파일 확장자 필터링
						// 로컬 테스트
						Path targetFilePath = targetPath.resolve(entry.getFileName());
						Files.copy(entry, targetFilePath, StandardCopyOption.REPLACE_EXISTING);
						log.info("동기화된 파일: {}", targetFilePath);
						try {
							remoteFileService.uploadFile(entry, entry.getFileName().toString());
						} catch (Exception e) {
							log.error("파일 업로드 중 오류 발생: {}", entry.getFileName(), e);
						}

					}
				}
			}*/
			log.info("최초 동기화 완료!");
		} catch (IOException e) {
			log.error("최초 동기화 오류: ", e);
			throw e;
		}

		// 2. WatchService 시작
		try {
			WatchService watchService = FileSystems.getDefault().newWatchService();
			log.info("폴더 와치 서비스 시작!");

			Path sourcePath = Paths.get(sourceDir);
			sourcePath.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
					StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);

			Thread thread = new Thread(() -> {
				try {
					while (true) {
						try {
							watchKey = watchService.take();
						} catch (InterruptedException e) {
							log.error("와치 서비스 에러: ", e);
							return;
						}

						List<WatchEvent<?>> watchEvents = watchKey.pollEvents();
						for (WatchEvent<?> watchEvent : watchEvents) {
							WatchEvent.Kind<?> kind = watchEvent.kind();

							// 감지된 파일 이름과 경로
							Path fileName = (Path) watchEvent.context();
							Path detectedFilePath = sourcePath.resolve(fileName);

							log.info("감지됨: {} - {}", kind.name(), fileName.getFileName());

							if (!isAllowedExtension(fileName)) {
								log.info("확장자가 일치하지 않음(무시): {}", fileName.getFileName());
								continue;
							}

							if (kind.equals(StandardWatchEventKinds.ENTRY_CREATE)) {
								// 파일 생성 복사
								log.info("파일 생성됨: {}", fileName.getFileName());
								// 로컬 카피 테스트
								/*try {
									Path targetPath = Paths.get(targetDir).resolve(fileName);
									Files.createDirectories(Paths.get(targetDir)); // 대상 폴더가 없을 경우 생성
									Files.copy(detectedFilePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
									log.info("복사된 파일: {}", targetPath);
								} catch (IOException e) {
									log.error("파일 생성 복사 에러: {}", fileName.getFileName(), e);
								}*/
								try {
									Path localFilePath = sourcePath.resolve(fileName);
									// 업로드 요청
									remoteFileService.uploadFile(localFilePath, fileName.toString());
								} catch (Exception e) {
									log.error("파일 업로드 요청 중 오류 발생: {}", fileName.getFileName(), e);
								}

							} else if (kind.equals(StandardWatchEventKinds.ENTRY_MODIFY)) {
								// 파일 수정 복사
								log.info("파일 수정됨: {}", fileName.getFileName());
								// 로컬 카피 테스트
								/*try {
									Path targetPath = Paths.get(targetDir).resolve(fileName);
									Files.createDirectories(Paths.get(targetDir)); // 대상 폴더가 없을 경우 생성
									Files.copy(detectedFilePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
									log.info("복사된 파일: {}", targetPath);
								} catch (IOException e) {
									log.error("파일 수정 복사 에러: {}", fileName.getFileName(), e);
								}*/
								try {
									Path localFilePath = sourcePath.resolve(fileName);
									// 업로드 요청
									remoteFileService.uploadFile(localFilePath, fileName.toString());
								} catch (Exception e) {
									log.error("파일 업로드 요청 중 오류 발생: {}", fileName.getFileName(), e);
								}
							} else if (kind.equals(StandardWatchEventKinds.ENTRY_DELETE)) {
								// 파일 삭제 처리
								log.info("파일 삭제됨: {}", fileName.getFileName());
								// 로컬 삭제 테스트
								/*try {
									Path targetPath = Paths.get(targetDir).resolve(fileName);
									if (Files.deleteIfExists(targetPath)) {
										log.info("타겟 폴더에서 파일 삭제됨: {}", targetPath);
									} else {
										log.warn("타겟 폴더에서 파일을 찾을 수 없음: {}", targetPath);
									}
								} catch (IOException e) {
									log.error("타겟 폴더 파일 삭제 에러: {}", fileName.getFileName(), e);
								}*/
								try {
									// 삭제 요청
									remoteFileService.deleteFile(fileName.toString());
								} catch (Exception e) {
									log.error("파일 삭제 요청 중 오류 발생: {}", fileName.getFileName(), e);
								}

							}
						}

						if (!watchKey.reset()) {
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

	// 파일 확장자 필터링 로직
	private boolean isAllowedExtension(Path file) {
		String fileName = file.getFileName().toString();
		int lastDotIndex = fileName.lastIndexOf(".");
		if (lastDotIndex == -1) {
			return false; // 확장자가 없는 경우
		}
		// 파일 확장자를 소문자로 변환하여 필터링
		String fileExtension = fileName.substring(lastDotIndex + 1).toLowerCase();
		return allowedExtensions.contains(fileExtension);
	}

}