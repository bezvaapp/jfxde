package dev.jfxde.sysapps.jshell.commands;

import dev.jfxde.jfx.scene.control.InternalDialog;
import dev.jfxde.sysapps.jshell.CommandProcessor;
import dev.jfxde.sysapps.jshell.SetBox;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.stage.Modality;
import picocli.CommandLine.Command;

@Command(name = "/set")
public class SetCommand extends BaseCommand {

    public SetCommand(CommandProcessor commandProcessor) {
        super(commandProcessor);
    }

    @Override
    public void run() {
        Platform.runLater(() -> {

            InternalDialog dialog = new InternalDialog(commandProcessor.getSession().getContent(), Modality.WINDOW_MODAL);
            dialog.setTitle(commandProcessor.getSession().getContext().rc().getString("settings"));
            SetBox setBox = new SetBox(commandProcessor.getSession().getContext(), commandProcessor.getSession().loadSettings());
            DialogPane dialogPane = new DialogPane();
            dialogPane.setContent(setBox);
            ButtonType okButtonType = new ButtonType(commandProcessor.getSession().getContext().rc().getString("ok"), ButtonData.OK_DONE);
            ButtonType cancelButtonType = new ButtonType(commandProcessor.getSession().getContext().rc().getString("cancel"),
                    ButtonData.CANCEL_CLOSE);
            dialogPane.getButtonTypes().addAll(okButtonType, cancelButtonType);
            final Button btOk = (Button) dialogPane.lookupButton(okButtonType);
            btOk.setOnAction(e -> {
                dialog.close();
                commandProcessor.getSession().setSettings(setBox.getSettings());
            });
            final Button cancel = (Button) dialogPane.lookupButton(cancelButtonType);
            cancel.setOnAction(e -> dialog.close());

            dialog.setContent(dialogPane).show();
        });
    }
}
