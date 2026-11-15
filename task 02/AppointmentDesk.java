import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/** Task 2 - Hospital Outpatient Appointment Desk. Every event prints a trace line to the console (Task 2.4). */
public class AppointmentDesk extends JFrame {
    // doctors are loaded ONCE into a Map (department -> doctors)
    private final Map<String, List<String>> doctorMap = new LinkedHashMap<>();
    private final List<String> allSlots = List.of("09:00", "09:30", "10:00", "10:30", "11:00");
    private final Set<String> booked = new HashSet<>();           // "doctor|slot" already taken

    private final JTextField patientId = new JTextField(12);
    private final JComboBox<String> department = new JComboBox<>();
    private final JComboBox<String> doctor = new JComboBox<>();
    private final JComboBox<String> slot = new JComboBox<>();
    private final JButton book = new JButton("Book");
    private final JButton clear = new JButton("Clear");
    private final JLabel status = new JLabel(" ");
    private boolean inProgress;                                   // a booking is being entered or saved
    private boolean updating;                                     // true while the program itself refills a list

    public AppointmentDesk() {
        super("OPD Appointment Desk");
        log("constructor: building components");

        doctorMap.put("Cardiology",  List.of("Dr. Rao", "Dr. Mehta"));
        doctorMap.put("Neurology",   List.of("Dr. Iyer", "Dr. Khan"));
        doctorMap.put("Orthopedics", List.of("Dr. Singh", "Dr. Das"));
        for (String d : doctorMap.keySet()) department.addItem(d);
        department.setSelectedIndex(-1);
        doctor.setEnabled(false);
        slot.setEnabled(false);

        // ---- containment tree: JFrame > form panel (GridLayout) + south panel (buttons + status) ----
        JPanel form = new JPanel(new GridLayout(4, 2, 6, 6));
        form.setBorder(BorderFactory.createEmptyBorder(10, 10, 4, 10));
        form.add(new JLabel("Patient ID:")); form.add(patientId);
        form.add(new JLabel("Department:")); form.add(department);
        form.add(new JLabel("Doctor:"));     form.add(doctor);
        form.add(new JLabel("Time slot:"));  form.add(slot);
        JPanel buttons = new JPanel(new FlowLayout());
        buttons.add(book); buttons.add(clear);
        JPanel south = new JPanel(new BorderLayout());
        south.add(buttons, BorderLayout.CENTER);
        south.add(status, BorderLayout.SOUTH);
        status.setBorder(BorderFactory.createEmptyBorder(0, 10, 8, 10));
        add(new JLabel("Outpatient Appointment Desk", SwingConstants.CENTER), BorderLayout.NORTH);
        add(form, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        // ---- event sources and listeners (Task 2.1) ----
        patientId.addActionListener(e -> { log("ActionEvent  patientId (Enter)"); checkPatientId(); });
        patientId.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { log("FocusEvent   patientId (focus lost)"); checkPatientId(); }
        });
        patientId.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { inProgress = true; }
            public void removeUpdate(DocumentEvent e)  { inProgress = true; }
            public void changedUpdate(DocumentEvent e) { inProgress = true; }
        });
        department.addItemListener(e -> {
            if (!updating && e.getStateChange() == ItemEvent.SELECTED) { log("ItemEvent    department -> " + e.getItem()); refreshDoctors(); }
        });
        doctor.addItemListener(e -> {
            if (!updating && e.getStateChange() == ItemEvent.SELECTED) { log("ItemEvent    doctor -> " + e.getItem()); loadSlots(); }
        });
        slot.addItemListener(e -> {
            if (!updating && e.getStateChange() == ItemEvent.SELECTED) log("ItemEvent    slot -> " + e.getItem());
        });
        book.addActionListener(e -> { log("ActionEvent  Book"); book(); });
        clear.addActionListener(e -> { log("ActionEvent  Clear"); clearForm(); });

        // window events (Task 2.4) - the trace lines show the order of the window methods
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent e)      { log("window: windowOpened"); }
            @Override public void windowActivated(WindowEvent e)   { log("window: windowActivated"); }
            @Override public void windowDeactivated(WindowEvent e) { log("window: windowDeactivated"); }
            @Override public void windowIconified(WindowEvent e)   { log("window: windowIconified"); }
            @Override public void windowDeiconified(WindowEvent e) { log("window: windowDeiconified"); }
            @Override public void windowClosed(WindowEvent e)      { log("window: windowClosed"); }
            @Override public void windowClosing(WindowEvent e) {
                log("window: windowClosing");
                if (inProgress && JOptionPane.showConfirmDialog(AppointmentDesk.this,
                        "A booking is in progress. Close and discard it?", "Confirm close",
                        JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
                dispose();
            }
        });
        pack();
        setLocationRelativeTo(null);
    }

    /** (a) Refresh the doctor list when the department changes. */
    private void refreshDoctors() {
        String dept = (String) department.getSelectedItem();
        updating = true;
        doctor.removeAllItems();
        slot.removeAllItems();
        slot.setEnabled(false);
        inProgress = true;
        List<String> list = (dept == null) ? null : doctorMap.get(dept);
        if (list == null || list.isEmpty()) {
            updating = false;
            doctor.setEnabled(false);
            show("No doctors available", true);
            return;
        }
        for (String d : list) doctor.addItem(d);
        doctor.setSelectedIndex(-1);
        updating = false;
        doctor.setEnabled(true);
        show("Choose a doctor", false);
    }

    private void loadSlots() {
        String doc = (String) doctor.getSelectedItem();
        updating = true;
        slot.removeAllItems();
        if (doc == null) { updating = false; slot.setEnabled(false); return; }
        for (String s : allSlots) if (!booked.contains(doc + "|" + s)) slot.addItem(s);   // only free slots
        slot.setSelectedIndex(-1);
        updating = false;
        slot.setEnabled(slot.getItemCount() > 0);
        if (slot.getItemCount() == 0) show(doc + " has no free slots", true);
    }

    private boolean checkPatientId() {
        boolean ok = patientId.getText().trim().matches("[A-Za-z0-9]{4,12}");
        if (!ok) show("Invalid patient ID (4-12 letters/digits)", true);
        return ok;
    }

    /** (b) Validate and book the appointment. */
    private void book() {
        List<String> errors = new ArrayList<>();
        if (!patientId.getText().trim().matches("[A-Za-z0-9]{4,12}")) errors.add("Invalid patient ID (4-12 letters/digits)");
        final String id = patientId.getText().trim();
        final String dept = (String) department.getSelectedItem();
        final String doc = (String) doctor.getSelectedItem();
        final String time = (String) slot.getSelectedItem();
        if (dept == null) errors.add("Choose a department");
        if (doc == null)  errors.add("Choose a doctor");
        if (time == null) errors.add("Choose a time slot");
        if (!errors.isEmpty()) { show(String.join("; ", errors), true); return; }   // nothing is saved

        book.setEnabled(false);
        inProgress = true;
        show("Booking...", false);
        new SwingWorker<String, Void>() {                      // stands in for the database call
            @Override protected String doInBackground() throws Exception {
                Thread.sleep(1000);
                synchronized (booked) {
                    if (!booked.add(doc + "|" + time)) throw new IllegalStateException("Slot already taken");
                }
                return "Confirmed: " + id + " with " + doc + " (" + dept + ") at " + time;
            }
            @Override protected void done() {
                book.setEnabled(true);
                try {
                    show(get(), false);
                    inProgress = false;
                    loadSlots();                               // the booked slot disappears
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    show("Error: " + ex.getCause().getMessage(), true);
                }
            }
        }.execute();
    }

    private void clearForm() {
        updating = true;
        patientId.setText("");
        department.setSelectedIndex(-1);
        doctor.removeAllItems(); doctor.setEnabled(false);
        slot.removeAllItems();   slot.setEnabled(false);
        updating = false;
        show(" ", false);
        inProgress = false;
    }

    private void show(String msg, boolean error) {
        status.setForeground(error ? new Color(170, 0, 0) : new Color(0, 110, 0));
        status.setText(msg);
    }

    private static void log(String s) { System.out.println("[trace] " + s); }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            AppointmentDesk w = new AppointmentDesk();
            log("setVisible(true)");
            w.setVisible(true);
        });
    }
}
