package FileSync.FindFileSync;

import org.springframework.core.io.FileSystemResource;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileSystemResourceToMultipartFile implements MultipartFile {

    private final FileSystemResource fileSystemResource;

    public FileSystemResourceToMultipartFile(FileSystemResource fileSystemResource) {
        this.fileSystemResource = fileSystemResource;
    }

    @Override
    public String getName() {
        return fileSystemResource.getFilename();
    }

    @Override
    public String getOriginalFilename() {
        return fileSystemResource.getFilename();
    }

    @Override
    public String getContentType() {
        return "";
    }

    @Override
    public boolean isEmpty() {
        try {
            return fileSystemResource.exists() && fileSystemResource.contentLength() == 0;
        } catch (IOException e) {
            return true;
        }
    }

    @Override
    public long getSize() {
        try {
            return fileSystemResource.contentLength();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] getBytes() throws IOException {
        try (InputStream inputStream = fileSystemResource.getInputStream()) {
            return inputStream.readAllBytes();
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return fileSystemResource.getInputStream();
    }

    @Override
    public void transferTo(File dest) throws IOException, IllegalStateException {
        // fileSystemResource에서 파일을 읽어와서 dest로 복사
        try (InputStream in = fileSystemResource.getInputStream()) {
            Files.copy(in, Paths.get(dest.getAbsolutePath()));
        }
    }
}
