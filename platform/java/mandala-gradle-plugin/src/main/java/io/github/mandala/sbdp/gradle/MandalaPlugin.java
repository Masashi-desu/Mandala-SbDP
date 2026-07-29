package io.github.mandala.sbdp.gradle;

import io.github.mandala.sbdp.cli.MandalaCli;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import java.util.ArrayList;
import java.util.List;

public final class MandalaPlugin implements Plugin<Project> {
    @Override public void apply(Project project) {
        register(project, "mandalaDiscover", "discover");
        register(project, "mandalaRefresh", "refresh");
        register(project, "mandalaRender", "render");
        register(project, "mandalaVerify", "verify");
        register(project, "mandalaDiff", "diff");
    }

    private void register(Project project, String taskName, String command) {
        project.getTasks().register(taskName, MandalaTask.class, task -> {
            task.setGroup("documentation"); task.setDescription("Run `mandala " + command + "`"); task.getCommand().set(command);
        });
    }

    public abstract static class MandalaTask extends DefaultTask {
        @Input public abstract org.gradle.api.provider.Property<String> getCommand();
        @Input public abstract org.gradle.api.provider.ListProperty<String> getArguments();

        public MandalaTask() { getArguments().convention(List.of()); }

        @TaskAction public void runMandala() {
            List<String> args = new ArrayList<>();
            args.add("--repository"); args.add(getProject().getRootDir().getAbsolutePath()); args.add(getCommand().get()); args.addAll(getArguments().get());
            int exit = MandalaCli.execute(args.toArray(String[]::new));
            if (exit != 0) throw new GradleException("Mandala exited with code " + exit);
        }
    }
}
