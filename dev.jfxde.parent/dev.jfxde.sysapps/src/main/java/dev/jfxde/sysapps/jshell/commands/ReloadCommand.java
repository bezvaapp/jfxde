package dev.jfxde.sysapps.jshell.commands;

import dev.jfxde.jfx.scene.control.ConsoleModel;
import dev.jfxde.sysapps.jshell.CommandProcessor;
import picocli.CommandLine.Command;

@Command(name = "/reload")
public class ReloadCommand extends BaseCommand {

    public ReloadCommand(CommandProcessor commandProcessor) {
        super(commandProcessor);
    }

    @Override
    public void run() {

        commandProcessor.getSession().getFeedback().normaln(commandProcessor.getSession().getContext().rc().getString("reloadingState"), ConsoleModel.COMMENT_STYLE);
        commandProcessor.getSession().reload();
    }
}
