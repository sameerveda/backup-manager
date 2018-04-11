package sam.backup.manager.config.view;

import sam.backup.manager.file.FileTreeEntity;

public interface FilesViewMode {
	public static FilesViewMode BACKUP = new FilesViewMode() {
		@Override public void set(FileTreeEntity ft, boolean value) { ft.setBackupable(value); }
		@Override public boolean isSelectable() { return true; }
		@Override public boolean get(FileTreeEntity file) {return file.isBackupable();}
	}; 
	public static FilesViewMode DELETE = new FilesViewMode() {
		@Override public void set(FileTreeEntity ft, boolean value) { ft.setDeletable(value); }
		@Override public boolean isSelectable() { return true; }
		@Override public boolean get(FileTreeEntity file) {return file.isDeletable();}
		
	};;
	public static FilesViewMode ALL = new FilesViewMode() {
		@Override public void set(FileTreeEntity ft, boolean value) { }
		@Override public boolean isSelectable() { return false; }
		@Override public boolean get(FileTreeEntity file) {return false;}
	};
	
	public void set(FileTreeEntity entity, boolean value);
	public boolean isSelectable();
	public boolean get(FileTreeEntity entity);
}