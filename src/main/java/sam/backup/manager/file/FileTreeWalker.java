package sam.backup.manager.file;

import java.nio.file.FileVisitResult;

public interface FileTreeWalker {
	public FileVisitResult file(FileEntity ft, AboutFile source, AboutFile backup);
	public FileVisitResult dir(DirEntity ft, AboutFile source, AboutFile backup);
}
