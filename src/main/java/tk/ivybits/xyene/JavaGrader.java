package tk.ivybits.xyene;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Sketchy Swing UI to help grading student assignments.
 *
 * @author Tudor Brindus (Xyene)
 */
public class JavaGrader extends JFrame {
    public JavaGrader() {
        super("Java Grader");

        JTabbedPane tabs = new JTabbedPane();
        JTextPane output = new JTextPane();
        JTextPane code = new JTextPane();
        Font monoFont = new Font(Font.MONOSPACED, Font.PLAIN, 14);
        output.setFont(monoFont);
        output.setEditable(false);
        code.setFont(monoFont);
        code.setEditable(false);

        JTextField input = new JTextField();

        JPanel IO = new JPanel(new BorderLayout());
        IO.add(new JScrollPane(output), BorderLayout.CENTER);
        IO.add(input, BorderLayout.SOUTH);

        tabs.addTab("IO", IO);
        tabs.addTab("Source", new JScrollPane(code));

        JLabel drop = new JLabel("<html><b>Drop file to run here!</b><html>", JLabel.CENTER);

        setLayout(new BorderLayout());

        add(tabs, BorderLayout.CENTER);
        add(drop, BorderLayout.SOUTH);

        setSize(new Dimension(640, 480));
        drop.setPreferredSize(new Dimension(0, 125));

        drop.setDropTarget(new DropTarget() {
            public synchronized void drop(DropTargetDropEvent evt) {
                try {
                    evt.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> files = (List<File>) evt.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    output.setText("");
                    File path = files.get(0);
                    drop.setText(String.format("<html>%s<br/><b>Drop file to run here!</b<</html>", path.toString()));
                    new Thread(() -> {
                        try {
                            spawnAltJVM(path, output, input);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }).start();
                    code.setText(new String(Files.readAllBytes(path.toPath()), StandardCharsets.UTF_8));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);
    }

    public static void spawnAltJVM(File cp, JTextPane out, JTextField in) throws IOException, InterruptedException, ClassNotFoundException {
        System.out.println("Using agent " + cp);

        Process compiler = new ProcessBuilder(
                System.getProperty("jdk.home") + File.separator + "bin" + File.separator + "javac",
                cp.getName()
        ).directory(cp.getParentFile()).start();
        redirectOutput(out, compiler.getInputStream());
        redirectOutput(out, compiler.getErrorStream());

        compiler.waitFor();

        ProcessBuilder processBuilder = new ProcessBuilder(
                System.getProperty("java.home") + File.separator + "bin" + File.separator + "java",
                "-Xmx384m",
                "-Xss2m",
                "-showversion",
                "-cp", ".",
                cp.getName().replace(".java", "")
        ).directory(cp.getParentFile());

        Process applet = processBuilder.start();

        redirectOutput(out, applet.getInputStream());
        redirectOutput(out, applet.getErrorStream());
        PrintStream to = new PrintStream(applet.getOutputStream());
        KeyAdapter key;
        in.addKeyListener(key = new KeyAdapter() {
            String buffer = "";

            @Override
            public void keyTyped(KeyEvent e) {
                buffer += e.getKeyChar();
                if (e.getKeyChar() == '\n') {
                    out.setText(out.getText() + buffer);
                    to.print(buffer);
                    to.flush();
                    buffer = "";
                    in.setText("");
                }
            }
        });
        applet.waitFor();
        in.removeKeyListener(key);
    }

    public static void redirectOutput(JTextPane out, InputStream in) {
        new Thread(() -> {
            try {
                byte[] buffer = new byte[65536];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    final int _len = len;
                    SwingUtilities.invokeAndWait(() -> out.setText(out.getText() + new String(buffer, 0, _len, StandardCharsets.US_ASCII)));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public static void main(String[] argv) throws ClassNotFoundException, UnsupportedLookAndFeelException, InstantiationException, IllegalAccessException {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeLater(JavaGrader::new);
    }
}
