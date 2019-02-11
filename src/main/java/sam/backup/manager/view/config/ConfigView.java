package sam.backup.manager.view.config;


import static java.lang.String.valueOf;
import static javafx.application.Platform.runLater;
import static sam.backup.manager.extra.Utils.bytesToString;
import static sam.backup.manager.extra.Utils.hyperlink;
import static sam.backup.manager.extra.Utils.millsToTimeString;
import static sam.backup.manager.extra.Utils.showErrorDialog;
import static sam.backup.manager.extra.Utils.writeInTempDir;
import static sam.backup.manager.view.ButtonType.DELETE;
import static sam.backup.manager.view.ButtonType.FILES;
import static sam.backup.manager.view.ButtonType.WALK;
import static sam.fx.helpers.FxClassHelper.addClass;
import static sam.fx.helpers.FxClassHelper.removeClass;
import static sam.fx.helpers.FxMenu.menuitem;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;

import org.slf4j.Logger;

import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import sam.backup.manager.config.api.Config;
import sam.backup.manager.extra.TreeType;
import sam.backup.manager.extra.Utils;
import sam.backup.manager.file.Dir;
import sam.backup.manager.file.FileEntity;
import sam.backup.manager.file.FileTree;
import sam.backup.manager.file.FileTreeString;
import sam.backup.manager.file.FilteredFileTree;
import sam.backup.manager.view.ButtonAction;
import sam.backup.manager.view.ButtonType;
import sam.backup.manager.view.CustomButton;
import sam.backup.manager.view.WalkHandler;
import sam.backup.manager.walk.WalkListener;
import sam.backup.manager.walk.WalkMode;
import sam.fx.helpers.FxLabel;
import sam.fx.helpers.FxText;
import sam.fx.popup.FxPopupShop;

public class ConfigView extends BorderPane implements ButtonAction, WalkListener {
	private static final Logger LOGGER = Utils.getLogger(ConfigView.class);

	private final Config config;
	private final VBox container = new VBox(5);
	private final CustomButton files = new CustomButton(FILES, this);
	private final CustomButton delete = new CustomButton(DELETE, this);
	private final CustomButton walk = new CustomButton(WALK, this); 
	private final Text sourceSizeT, targetSizeT, sourceFileCountT; 
	private final Text sourceDirCountT, targetFileCountT, targetDirCountT;
	private final Text backupSizeT, backupFileCountT;
	private final WalkHandler handler;

	private final Text bottomText;

	public ConfigView(Config config, Long lastUpdated, WalkHandler handler) {
		this.config = config;
		addClass(this, "config-view");
		addClass(container, "grid");
		setContextMenu();
		this.handler = handler;

		Label l = FxLabel.label(String.valueOf(config.getSource()),"title");
		l.setMaxWidth(Double.MAX_VALUE);
		setTop(l);
		l.setOnMouseClicked(e -> {
			if(getCenter() == null)
				setCenter(container);
			else 
				getChildren().remove(container);
		});

		sourceSizeT = text("---");
		sourceFileCountT = text("---");
		sourceDirCountT = text("---"); 

		String st = config.getWalkConfig().walkBackup() ? "--" : "N/A";
		targetSizeT = text(st); 
		targetFileCountT = text(st); 
		targetDirCountT = text(st);

		backupSizeT = text("---");
		backupFileCountT = text("---");

		ObservableList<Node> container = this.container.getChildren();

		container.add(text("Source: "));
		container.addAll(new Text("  "),  hyperlink(config.getSource()));
		container.add(text("Target: "));
		container.addAll(new Text("  "),  hyperlink(config.getBaseTarget()));
		container.add(new HBox(5, text("Last updated: "), text(lastUpdated == null ? "N/A" : millsToTimeString(lastUpdated))));

		Label t = FxLabel.label("SUMMERY", "summery");
		t.setMaxWidth(Double.MAX_VALUE);
		t.setAlignment(Pos.CENTER);
		container.add(t);
		
		TilePane tiles = new TilePane(2, 2,
				new Text(), header("Source"), header("Backup"), header("New/Modified"),
				new Text("size  |"), sourceSizeT, targetSizeT, backupSizeT,
				new Text("files |"), sourceFileCountT, targetFileCountT, backupFileCountT,
				new Text("dirs  |"), sourceDirCountT, targetDirCountT);

		container.add(tiles);

		if(config.getSource().stream().allMatch(p -> p.path() == null || Files.notExists(p.path())))
			finish("Source not found", true);
		else {
			container.add(new HBox(5, walk, files, delete));
			files.setVisible(false);
			delete.setVisible(false);
		}
		
		bottomText = new Text();
		container.add(bottomText);
	}
	private void setContextMenu() {
		setOnContextMenuRequested(e -> {
			ContextMenu menu = new ContextMenu( 
					menuitem("Set as latest", this::setAsLatestAction, backupFFT.isNull()),
					menuitem("All files", this::allfilesAction)
					// menuitem("clean backup", e1 -> new BackupCleanup(config))
					) ;
			menu.show(Utils.window(), e.getScreenX(), e.getScreenY());
		});
	}
	private void allfilesAction(ActionEvent e) {
		if(backupFFT.get() == null)
			if(!loadFileTree())
				return;
		FilesView.open("all files",config, config.getFileTree(), FilesViewSelector.all());
	}
	private void setAsLatestAction(ActionEvent e) {
		config.getFileTree().forEach((s,t) -> t.forcedMarkUpdated());
		if(Utils.saveFileTree(config))
			FxPopupShop.showHidePopup("marked as letest", 1500);
	};
	private Node header(String string) {
		return addClass(new Label(string), "text", "header");
	}
	private Text text(String str) {
		return FxText.text(str, "text");
	}
	@Override
	public void handle(ButtonType type) {
		switch (type) {
			case FILES:
				FilesView.open("select files to backup", config, backupFFT.get(), FilesViewSelector.backup());
				break;
			case DELETE:
				CustomButton b = new CustomButton(DELETE);
				Stage stage = FilesView.open("select files to delete", config, deleteFFT.get(), FilesViewSelector.delete(), b);
				b.setOnAction(e -> deleteAction(stage));
				break;
			case WALK:
				walk.setType(ButtonType.LOADING);
				handler.start(config, this);
				walk.setType(ButtonType.CANCEL);	
				break;
			case SET_MODIFIED:
				throw new IllegalStateException("not yet implemented");
			default:
				throw new IllegalArgumentException("unknown action: "+type);
		}
	}
	private void deleteAction(Stage stage) {
		writeInTempDir(config, "delete", null, new FileTreeString(deleteFFT.get()), LOGGER);
		stage.hide();

		//TODO old method Deleter.process(config.getFileTree(), deleteFFT.get())
		Deleter.process(deleteFFT.get())
		.thenAccept(NULL -> Utils.saveFileTree(config));
	}
	@Override
	public boolean isCancelled() {
		return cancel.get();
	}
	@Override
	public void stop() {
		cancel.set(true);
		walk.setType(ButtonType.WALK);
	}
	@Override
	public void start() {
		if(isCompleted())
			return;

		cancel.set(false);
		walk.setType(ButtonType.CANCEL);
		startEndAction.start(this);
	}
	public boolean isCompleted() {
		return walk != null && walk.isDisable();
	}
	public Config getConfig() {
		return config;
	}
	public void setError(String msg, Exception e) {
		if(e != null) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);

			finish(msg+"\n\n"+sw, true);
		}
		else 
			finish(msg, true);
	}
	@Override
	public void walkFailed(String reason, Throwable e) {
		finish(reason, true);
		if(e != null)
			e.printStackTrace();
	}

	private volatile long sourceSize, targetSize;
	private volatile int sourceFileCount, sourceDirCount, targetFileCount, targetDirCount;

	@Override
	public void onFileFound(FileEntity ft, long size, WalkMode mode) {
		runLater(() -> {
			if(mode == WalkMode.SOURCE) {
				sourceSizeT.setText(bytesToString(sourceSize += size));
				sourceFileCountT.setText(valueOf(++sourceFileCount));
			} else if(mode == WalkMode.BACKUP){
				targetSizeT.setText(bytesToString(targetSize += size));
				targetFileCountT.setText(valueOf(++targetFileCount));
			} else {
				throw new IllegalStateException("invalid walkMode: "+mode);
			}
		});
	}

	@Override
	public void onDirFound(Dir ft, WalkMode mode) {
		runLater(() -> {
			if(mode == WalkMode.SOURCE) 
				sourceDirCountT.setText(valueOf(++sourceDirCount));
			else if(mode == WalkMode.BACKUP)
				targetDirCountT.setText(valueOf(++targetDirCount));
			else 
				throw new IllegalStateException("invalid walkMode: "+mode);
		});
	}

	private final SimpleObjectProperty<FilteredFileTree>  backupFFT = new SimpleObjectProperty<>();
	private final SimpleObjectProperty<FilteredFileTree>  deleteFFT = new SimpleObjectProperty<>();

	@Override
	public void walkCompleted() {
		FilteredFileTree backup =  new FilteredFileTree(config.getFileTree(), f -> f.getStatus().isBackupable());
		FilteredFileTree delete = !config.getBackupConfig().hardSync() ? null :  new FilteredFileTree(config.getFileTree(), f -> f.getStatus().isBackupDeletable());

		runLater(() -> {
			backupFFT.set(backup);
			deleteFFT.set(delete);
			walk.setVisible(false);
		});

		if(!backup.isEmpty())
			updateBackupCounts(backup);
		else
			finish("Nothing to backup/delete", false);

		if(delete != null && !delete.isEmpty())
			updateDeleteCounts(delete);

		runLater(() -> startEndAction.onComplete(this));
	}
	private void updateDeleteCounts(FilteredFileTree deleteFT) {
		runLater(() -> delete.setVisible(true));
	}
	private void updateBackupCounts(FilteredFileTree backup) {
		runLater(() -> files.setVisible(true));

		long[] l = {0,0};
		walk(backup, l);
		runLater(() -> {
			backupSizeT.setText(bytesToString(l[1]));
			backupFileCountT.setText(String.valueOf(l[0]));
		});
	}
	private void walk(Dir backup, long[] l) {
		for (FileEntity f : backup) {
			if(f.isDirectory())
				walk((Dir)f, l);
			else {
				l[0]++;
				l[1] += f.getSourceAttrs().size();	
			}
		}
	}
	public void finish(String msg, boolean failed) {
		if(failed) {
			config.isDisabled();
		}
		removeClass(this, "disable", "completed");
		removeClass(bottomText, "disable-text", "completed-text");
		String s = failed ? "disable" : "completed";
		addClass(this, s);
		addClass(bottomText, s+"-text");
		bottomText.setText(msg);
	}
	public boolean hashBackups() {
		return backupFFT.get() != null && !backupFFT.get().isEmpty();
	}
	public FilteredFileTree getBackupFileTree() {
		return backupFFT.get();
	}
	public FilteredFileTree getDeleteFileTree() {
		return deleteFFT.get();
	}
	public boolean hashDeleteBackups() {
		return deleteFFT.get() != null && !deleteFFT.get().isEmpty();
	}
	public FilteredFileTree getDeleteBackups() {
		return deleteFFT.get();
	}
	public boolean loadFileTree() {
		if(config.getFileTree() != null)
			return true;

		if(config.getWalkConfig().getDepth() <= 0) {
			runLater(() -> finish("Walk failed: \nbad value for depth: "+config.getWalkConfig().getDepth(), true));
			return false;
		}
		if(config.getFileTree() == null) {
			FileTree ft;
			try {
				ft = Utils.readFiletree(config, TreeType.BACKUP, false);
			} catch (Exception e) {
				showErrorDialog(null, "failed to read TreeFile: ", e);
				LOGGER.error("failed to read TreeFile: ", e);
				return false;
			}
			if(ft == null) {
				try {
					ft = Utils.readFiletree(config, TreeType.BACKUP, true);
				} catch (Exception e) {
					showErrorDialog(null, "failed to read TreeFile: ", e);
					LOGGER.error("failed to read TreeFile: ", e);
					return false;	
				} 
				config.setFileTree(ft);	
			} else
				config.setFileTree(ft);
			return true;
		}
		return false;
	}
}