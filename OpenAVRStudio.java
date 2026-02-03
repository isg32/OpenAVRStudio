import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.*;

public class OpenAVRStudio extends JFrame {

    private static final String DEFAULT_MCU = "atmega32";
    
    // State
    private File currentFile = null; // Tracks the currently open file
    
    // Components
    private JTextArea codeEditor;
    private JTextArea consoleOutput;
    private JTable registerTable;
    private DefaultTableModel registerModel;
    
    // Flags UI
    private JCheckBox flagZ, flagC, flagN, flagV;
    private JLabel pcLabel, spLabel;
    private JLabel statusLabel;

    // Undo/Redo
    private UndoManager undoManager;

    // Simulation State
    private boolean isRunning = false;
    private Thread simThread;

    public OpenAVRStudio() {
        super("OpenAVR Studio (Fedora Edition) - v5.2");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLayout(new BorderLayout());

        // --- MENU BAR ---
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        
        // 1. OPEN (Ctrl + O)
        JMenuItem openItem = new JMenuItem("Open...");
        openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
        openItem.addActionListener(e -> openFileAction());
        
        // 2. SAVE (Ctrl + S)
        JMenuItem saveItem = new JMenuItem("Save");
        saveItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
        saveItem.addActionListener(e -> quickSaveAction());

        // 3. SAVE AS (Ctrl + Shift + S)
        JMenuItem saveAsItem = new JMenuItem("Save As...");
        saveAsItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        saveAsItem.addActionListener(e -> saveAsAction());
        
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));

        fileMenu.add(openItem);
        fileMenu.add(saveItem);
        fileMenu.add(saveAsItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);
        setJMenuBar(menuBar);

        // --- TOOLBAR ---
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        
        JButton runButton = createButton("ASSEMBLE & RUN", new Color(0, 120, 60));
        runButton.addActionListener(e -> startBuildAndRun());
        
        JButton stopButton = createButton("STOP", new Color(180, 50, 50));
        stopButton.addActionListener(e -> stopSimulation());

        toolbar.add(runButton);
        toolbar.addSeparator();
        toolbar.add(stopButton);
        toolbar.addSeparator();
        toolbar.add(new JLabel(" Target: " + DEFAULT_MCU + "  "));
        add(toolbar, BorderLayout.NORTH);

        // --- MAIN SPLIT ---
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setResizeWeight(0.6);

        // LEFT: EDITOR
        codeEditor = new JTextArea(getLegacyStarterCode());
        codeEditor.setFont(new Font("Monospaced", Font.PLAIN, 15));
        codeEditor.setTabSize(4);
        codeEditor.setMargin(new Insets(5, 5, 5, 5));
        
        undoManager = new UndoManager();
        codeEditor.getDocument().addUndoableEditListener(e -> undoManager.addEdit(e.getEdit()));
        setupEditorKeyBindings();

        JScrollPane editorScroll = new JScrollPane(codeEditor);
        editorScroll.setBorder(BorderFactory.createTitledBorder("Assembly Editor"));
        mainSplit.setLeftComponent(editorScroll);

        // RIGHT: VISUALIZER
        JPanel rightPanel = new JPanel(new BorderLayout());
        
        // Registers
        JPanel statusPanel = new JPanel(new BorderLayout());
        String[] regCols = {"Reg", "Hex", "Dec"};
        registerModel = new DefaultTableModel(regCols, 0);
        for(int i=0; i<32; i++) registerModel.addRow(new Object[]{"R"+i, "00", "0"});
        
        registerTable = new JTable(registerModel);
        registerTable.setShowGrid(true);
        registerTable.setGridColor(Color.LIGHT_GRAY);
        registerTable.setFont(new Font("Monospaced", Font.PLAIN, 12));
        
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        for(int i=0; i<3; i++) registerTable.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);

        JScrollPane regScroll = new JScrollPane(registerTable);
        regScroll.setPreferredSize(new Dimension(300, 400));
        regScroll.setBorder(BorderFactory.createTitledBorder("Registers"));
        statusPanel.add(regScroll, BorderLayout.CENTER);

        // Flags
        JPanel flagsPanel = new JPanel(new GridLayout(4, 1, 5, 5));
        flagsPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        
        JPanel flagBitPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        flagBitPanel.setBorder(BorderFactory.createTitledBorder("SREG Flags"));
        flagZ = new JCheckBox("Z"); flagZ.setEnabled(false);
        flagC = new JCheckBox("C"); flagC.setEnabled(false);
        flagN = new JCheckBox("N"); flagN.setEnabled(false);
        flagV = new JCheckBox("V"); flagV.setEnabled(false);
        flagBitPanel.add(flagZ); flagBitPanel.add(flagC); flagBitPanel.add(flagN); flagBitPanel.add(flagV);
        
        pcLabel = new JLabel("PC: 0x0000");
        pcLabel.setFont(new Font("Monospaced", Font.BOLD, 14));
        pcLabel.setBorder(BorderFactory.createEtchedBorder());
        pcLabel.setHorizontalAlignment(SwingConstants.CENTER);

        spLabel = new JLabel("SP: 0x0000");
        spLabel.setFont(new Font("Monospaced", Font.BOLD, 14));
        spLabel.setBorder(BorderFactory.createEtchedBorder());
        spLabel.setHorizontalAlignment(SwingConstants.CENTER);

        flagsPanel.add(flagBitPanel);
        flagsPanel.add(pcLabel);
        flagsPanel.add(spLabel);
        
        statusPanel.add(flagsPanel, BorderLayout.NORTH);
        rightPanel.add(statusPanel, BorderLayout.CENTER);

        // Console
        consoleOutput = new JTextArea();
        consoleOutput.setEditable(false);
        consoleOutput.setBackground(new Color(30, 30, 30));
        consoleOutput.setForeground(new Color(100, 255, 100));
        consoleOutput.setFont(new Font("Monospaced", Font.PLAIN, 12));
        consoleOutput.setRows(10);
        consoleOutput.setFocusable(true); 
        
        JScrollPane consoleScroll = new JScrollPane(consoleOutput);
        consoleScroll.setBorder(BorderFactory.createTitledBorder("Console Log"));
        rightPanel.add(consoleScroll, BorderLayout.SOUTH);

        mainSplit.setRightComponent(rightPanel);
        add(mainSplit, BorderLayout.CENTER);
        
        statusLabel = new JLabel(" New File");
        statusLabel.setBorder(BorderFactory.createLoweredBevelBorder());
        add(statusLabel, BorderLayout.SOUTH);

        setVisible(true);
    }

    // --- FILE I/O ---
    
    private void openFileAction() {
        JFileChooser chooser = new JFileChooser(new File("."));
        chooser.setFileFilter(new FileNameExtensionFilter("AVR Assembly (*.S, *.asm)", "S", "asm", "inc"));
        
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            currentFile = chooser.getSelectedFile();
            try (BufferedReader reader = new BufferedReader(new FileReader(currentFile))) {
                codeEditor.read(reader, null);
                statusLabel.setText(" Opened: " + currentFile.getName());
                log("Loaded: " + currentFile.getAbsolutePath());
                // Reset undo manager on new file load
                undoManager.discardAllEdits();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
            }
        }
    }

    private void quickSaveAction() {
        if (currentFile != null) {
            saveToCurrentFile();
        } else {
            saveAsAction();
        }
    }

    private void saveAsAction() {
        JFileChooser chooser = new JFileChooser(new File("."));
        chooser.setFileFilter(new FileNameExtensionFilter("AVR Assembly (*.S)", "S"));
        
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".s") && !file.getName().toLowerCase().endsWith(".asm")) {
                file = new File(file.getParentFile(), file.getName() + ".S");
            }
            currentFile = file;
            saveToCurrentFile();
        }
    }

    private void saveToCurrentFile() {
        if (currentFile == null) return;
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(currentFile))) {
            codeEditor.write(writer);
            statusLabel.setText(" Saved: " + currentFile.getName());
            log("Saved: " + currentFile.getAbsolutePath());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Error saving: " + ex.getMessage());
        }
    }

    // --- HELPERS ---

    private JButton createButton(String text, Color bg) {
        JButton b = new JButton(text);
        b.setBackground(bg);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setFont(new Font("Arial", Font.BOLD, 12));
        return b;
    }

    private void setupEditorKeyBindings() {
        InputMap im = codeEditor.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = codeEditor.getActionMap();
        
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "Undo");
        am.put("Undo", new AbstractAction() { public void actionPerformed(ActionEvent e) { if (undoManager.canUndo()) undoManager.undo(); }});

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "Redo");
        am.put("Redo", new AbstractAction() { public void actionPerformed(ActionEvent e) { if (undoManager.canRedo()) undoManager.redo(); }});
    }

    private void startBuildAndRun() {
        stopSimulation();
        consoleOutput.setText("");
        log(">>> PROCESSING & ASSEMBLING...");

        String filename = "main";
        // Convert Legacy syntax to GCC before saving the temp build file
        String processedCode = convertAtmelToGcc(codeEditor.getText());
        
        try {
            // Save the processed code to main.S for gcc
            File sourceFile = new File(filename + ".S");
            FileWriter writer = new FileWriter(sourceFile);
            writer.write(processedCode);
            writer.close();
        } catch (IOException e) { log("Disk Error: " + e.getMessage()); return; }

        if (!runCommand("avr-gcc", "-mmcu=" + DEFAULT_MCU, "-nostdlib", "-o", filename + ".elf", filename + ".S")) return;
        if (!runCommand("avr-objcopy", "-O", "ihex", "-R", ".eeprom", filename + ".elf", filename + ".hex")) return;

        log(">>> BUILD SUCCESS. RUNNING SIMULATION...");
        startEmulator(filename + ".hex");
    }

    private String convertAtmelToGcc(String code) {
        StringBuilder sb = new StringBuilder();
        sb.append("#define __SFR_OFFSET 0\n");
        sb.append("#include <avr/io.h>\n");
        sb.append(".global main\n");
        sb.append("main:\n");

        String[] lines = code.split("\n");
        for (String line : lines) {
            String trim = line.trim().toUpperCase();
            if (trim.startsWith(".INCLUDE")) {
                sb.append("; ").append(line).append("\n"); // Comment out includes
            } else if (trim.startsWith("JMP ")) {
                sb.append("RJMP ").append(line.trim().substring(4)).append("\n");
            } else {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private void stopSimulation() {
        isRunning = false;
        if (simThread != null) {
            try { simThread.join(500); } catch (Exception e) {}
            simThread = null;
        }
    }

    private boolean runCommand(String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = r.readLine()) != null) log(line);
            return p.waitFor() == 0;
        } catch (Exception e) {
            log("Cmd Error: " + e.getMessage());
            return false;
        }
    }

    private void log(String msg) {
        consoleOutput.append(msg + "\n");
        consoleOutput.setCaretPosition(consoleOutput.getDocument().getLength());
    }

    // --- EMULATOR (With Flags Logic) ---
    private void startEmulator(String hexFile) {
        int[] flash = loadHex(hexFile);
        if (flash == null) return;

        isRunning = true;
        simThread = new Thread(() -> {
            int pc = 0;
            int[] r = new int[32]; 
            
            try {
                while (isRunning) {
                    if (pc >= flash.length) break;
                    
                    int opcode = flash[pc];
                    int nextPc = pc + 1;
                    boolean regChanged = false;

                    // LDI: 1110 KKKK dddd KKKK
                    if ((opcode & 0xF000) == 0xE000) {
                        int K = ((opcode & 0xF00) >> 4) | (opcode & 0x0F);
                        int d = ((opcode & 0x00F0) >> 4) + 16;
                        r[d] = K;
                        regChanged = true;
                    } 
                    // MOV: 0010 11rd dddd rrrr
                    else if ((opcode & 0xFC00) == 0x2C00) {
                        int d = (opcode & 0x01F0) >> 4;
                        int rr = ((opcode & 0x0200) >> 5) | (opcode & 0x000F);
                        r[d] = r[rr];
                        regChanged = true;
                    } 
                    // ANDI: 0111 KKKK dddd KKKK
                    else if ((opcode & 0xF000) == 0x7000) {
                        int K = ((opcode & 0xF00) >> 4) | (opcode & 0x0F);
                        int d = ((opcode & 0x00F0) >> 4) + 16;
                        r[d] = r[d] & K;
                        updateFlags(r[d]);
                        regChanged = true;
                    } 
                    // ORI: 0110 KKKK dddd KKKK
                    else if ((opcode & 0xF000) == 0x6000) {
                        int K = ((opcode & 0xF00) >> 4) | (opcode & 0x0F);
                        int d = ((opcode & 0x00F0) >> 4) + 16;
                        r[d] = r[d] | K;
                        updateFlags(r[d]);
                        regChanged = true;
                    } 
                    // SWAP: 1001 010d dddd 0010
                    else if ((opcode & 0xFE0F) == 0x9402) {
                        int d = (opcode & 0x01F0) >> 4;
                        int low = r[d] & 0x0F;
                        int high = (r[d] & 0xF0) >> 4;
                        r[d] = (low << 4) | high;
                        regChanged = true;
                    } 
                    // RJMP: 1100 kkkk kkkk kkkk
                    else if ((opcode & 0xF000) == 0xC000) {
                        int k = opcode & 0x0FFF;
                        if ((k & 0x800) != 0) k -= 4096;
                        nextPc = pc + 1 + k;
                    }

                    if (regChanged) updateGUI(r, pc);
                    pc = nextPc;
                    Thread.sleep(100); 
                }
            } catch (Exception e) { log("Sim Error: " + e.getMessage()); }
        });
        simThread.start();
    }

    private void updateFlags(int val) {
        SwingUtilities.invokeLater(() -> {
            flagZ.setSelected(val == 0);
            flagN.setSelected((val & 0x80) != 0);
        });
    }

    private void updateGUI(int[] r, int pc) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < 32; i++) {
                String hex = String.format("%02X", r[i]);
                if (!registerModel.getValueAt(i, 1).equals(hex)) {
                    registerModel.setValueAt(hex, i, 1);
                    registerModel.setValueAt(r[i], i, 2);
                }
            }
            pcLabel.setText(String.format("PC: 0x%04X", pc * 2));
        });
    }

    private int[] loadHex(String filename) {
        int[] flash = new int[32768]; 
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.startsWith(":")) continue;
                int len = Integer.parseInt(line.substring(1, 3), 16);
                int addr = Integer.parseInt(line.substring(3, 7), 16) / 2; 
                int type = Integer.parseInt(line.substring(7, 9), 16);
                if (type == 00) { 
                    for (int i = 0; i < len; i += 2) {
                        int low = Integer.parseInt(line.substring(9 + i*2, 11 + i*2), 16);
                        int high = Integer.parseInt(line.substring(11 + i*2, 13 + i*2), 16);
                        flash[addr + (i/2)] = (high << 8) | low;
                    }
                }
            }
            return flash;
        } catch (Exception e) { return null; }
    }
    
    private String getLegacyStarterCode() {
        return ".INCLUDE \"M32DEF.INC\"\n\n" +
               "LDI R20,0x29\n" +
               "MOV R21,R20\n" +
               "ANDI R21,0x0F\n" +
               "ORI R21,0x30\n" +
               "\n" +
               "MOV R22,R20\n" +
               "SWAP R22\n" +
               "ANDI R22,0x0F\n" +
               "ORI R22,0x30\n" +
               "\n" +
               "HERE: JMP HERE\n";
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(OpenAVRStudio::new);
    }
}
