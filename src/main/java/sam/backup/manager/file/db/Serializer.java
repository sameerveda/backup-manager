package sam.backup.manager.file.db;

import java.util.function.Consumer;

interface Serializer<F extends FileEntity, D extends Dir> {
	F newFile(D parent, String filename);
	D newDir(D parent, String filename);
	void applyToAll(Consumer<FileImpl> action);
	FileTree getFileTree();
	void save() throws Exception;
	Attrs defaultAttrs();
}
