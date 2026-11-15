import java.awt.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/* ================= MODEL ================= */
record EventInfo(int id, String name) {
    public static final EventInfo ALL = new EventInfo(-1, "All events");   // "no filter" item
    public boolean isAll() { return id < 0; }
    @Override public String toString() { return name; }
}

record Registration(int id, String regNo, String name, String email, String event, String registeredAt) { }

/* ================= DATA-ACCESS LAYER (JDBC) ================= */
interface RegistrationRepository {
    interface RowSink { void accept(Registration r) throws IOException; }
    List<EventInfo> findEvents() throws SQLException;
    boolean exists(String regNo, int eventId) throws SQLException;
    void insert(String regNo, String name, String email, int eventId) throws SQLException;
    int count(Integer eventId) throws SQLException;                                  // null = all events
    List<Registration> page(Integer eventId, int limit, int offset) throws SQLException;
    void stream(Integer eventId, RowSink sink) throws SQLException, IOException;     // for export
}

final class Db {
    static final String URL = "jdbc:mysql://localhost:3306/campus_events?useSSL=false&allowPublicKeyRetrieval=true";
    static final String USER = "root";
    static final String PASSWORD = "your_password";      // <-- change this
    private Db() { }
    static Connection connect() throws SQLException { return DriverManager.getConnection(URL, USER, PASSWORD); }
}

class JdbcRegistrationRepository implements RegistrationRepository {
    private static final String SELECT_ROWS =
            "SELECT r.reg_id, r.student_reg_no, r.student_name, r.email, e.name, r.registered_at "
          + "FROM registrations r JOIN events e ON e.event_id = r.event_id ";

    @Override public List<EventInfo> findEvents() throws SQLException {
        List<EventInfo> list = new ArrayList<>();
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement("SELECT event_id, name FROM events ORDER BY name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(new EventInfo(rs.getInt(1), rs.getString(2)));
        }
        return list;
    }

    @Override public boolean exists(String regNo, int eventId) throws SQLException {
        String sql = "SELECT 1 FROM registrations WHERE student_reg_no = ? AND event_id = ?";
        try (Connection c = Db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, regNo);
            ps.setInt(2, eventId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    @Override public void insert(String regNo, String name, String email, int eventId) throws SQLException {
        String sql = "INSERT INTO registrations (student_reg_no, student_name, email, event_id) VALUES (?, ?, ?, ?)";
        try (Connection c = Db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, regNo);
            ps.setString(2, name);
            ps.setString(3, email);
            ps.setInt(4, eventId);
            ps.executeUpdate();
        }
    }

    @Override public int count(Integer eventId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM registrations" + (eventId == null ? "" : " WHERE event_id = ?");
        try (Connection c = Db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (eventId != null) ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    @Override public List<Registration> page(Integer eventId, int limit, int offset) throws SQLException {
        String sql = SELECT_ROWS + (eventId == null ? "" : "WHERE r.event_id = ? ")
                   + "ORDER BY r.reg_id LIMIT ? OFFSET ?";
        List<Registration> rows = new ArrayList<>();
        try (Connection c = Db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            if (eventId != null) ps.setInt(i++, eventId);
            ps.setInt(i++, limit);
            ps.setInt(i, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(map(rs));
            }
        }
        return rows;
    }

    @Override public void stream(Integer eventId, RowSink sink) throws SQLException, IOException {
        String sql = SELECT_ROWS + (eventId == null ? "" : "WHERE r.event_id = ? ") + "ORDER BY r.reg_id";
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            ps.setFetchSize(Integer.MIN_VALUE);          // MySQL: stream row by row
            if (eventId != null) ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) sink.accept(map(rs));
            }
        }
    }

    private static Registration map(ResultSet rs) throws SQLException {
        return new Registration(rs.getInt(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5), String.valueOf(rs.getTimestamp(6)));
    }
}

/* ================= SERVICE LAYER (rules, no Swing) ================= */
class ValidationException extends Exception {
    public ValidationException(String message) { super(message); }
}

class DuplicateRegistrationException extends Exception {
    public DuplicateRegistrationException(String message) { super(message); }
}

class RegistrationService {
    private final RegistrationRepository repo;

    public RegistrationService(RegistrationRepository repo) { this.repo = repo; }

    public List<EventInfo> events() throws SQLException { return repo.findEvents(); }
    public int count(EventInfo filter) throws SQLException { return repo.count(idOf(filter)); }
    public List<Registration> page(EventInfo filter, int pageSize, int pageIndex) throws SQLException {
        return repo.page(idOf(filter), pageSize, pageIndex * pageSize);
    }

    public void register(String regNo, String name, String email, EventInfo event)
            throws ValidationException, DuplicateRegistrationException, SQLException {
        regNo = regNo == null ? "" : regNo.trim();
        name = name == null ? "" : name.trim();
        email = email == null ? "" : email.trim();

        StringBuilder problems = new StringBuilder();
        if (!regNo.matches("[A-Za-z0-9]{6,15}"))
            problems.append("Register number must be 6-15 letters/digits.\n");
        if (!name.matches("[A-Za-z][A-Za-z .'-]{1,79}"))
            problems.append("Name must be 2-80 letters (spaces, . ' - allowed).\n");
        if (!email.matches("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+"))
            problems.append("Email address is not valid.\n");
        if (event == null || event.isAll())
            problems.append("Please choose an event.\n");
        if (problems.length() > 0) throw new ValidationException(problems.toString().trim());

        if (repo.exists(regNo, event.id()))
            throw new DuplicateRegistrationException(regNo + " is already registered for " + event.name() + ".");
        try {
            repo.insert(regNo, name, email, event.id());
        } catch (SQLIntegrityConstraintViolationException race) {
            // the UNIQUE key is the final guard if two desks register the same student at once
            throw new DuplicateRegistrationException(regNo + " is already registered for " + event.name() + ".");
        }
    }

    public int exportCsv(EventInfo filter, Writer out) throws SQLException, IOException {
        out.write("RegNo,Name,Email,Event,RegisteredAt\n");
        int[] n = {0};
        repo.stream(idOf(filter), r -> {
            out.write(csv(r.regNo()) + "," + csv(r.name()) + "," + csv(r.email()) + ","
                    + csv(r.event()) + "," + csv(r.registeredAt()) + "\n");
            n[0]++;
        });
        return n[0];
    }

    static String csv(String s) {
        boolean quote = s.contains(",") || s.contains("\"") || s.contains("\n");
        String t = s.replace("\"", "\"\"");
        return quote ? "\"" + t + "\"" : t;
    }

    private static Integer idOf(EventInfo e) { return (e == null || e.isAll()) ? null : e.id(); }
}

/* ================= UI LAYER ================= */
class RegisterDialog extends JDialog {
    private final JTextField regNo = new JTextField(14);
    private final JTextField name = new JTextField(20);
    private final JTextField email = new JTextField(20);
    private final JComboBox<EventInfo> event = new JComboBox<>();
    private final JButton register = new JButton("Register");
    private final JButton cancel = new JButton("Cancel");
    private final JTextArea message = new JTextArea(3, 28);
    private final RegistrationService service;
    private boolean registered;

    public RegisterDialog(JFrame owner, RegistrationService service, List<EventInfo> events) {
        super(owner, "New Registration", true);
        this.service = service;
        for (EventInfo e : events) event.addItem(e);
        event.setSelectedIndex(-1);

        JPanel form = new JPanel(new GridLayout(4, 2, 6, 6));
        form.setBorder(BorderFactory.createEmptyBorder(10, 10, 4, 10));
        form.add(new JLabel("Register No:")); form.add(regNo);
        form.add(new JLabel("Name:"));        form.add(name);
        form.add(new JLabel("Email:"));       form.add(email);
        form.add(new JLabel("Event:"));       form.add(event);

        message.setEditable(false);
        message.setOpaque(false);
        message.setLineWrap(true);
        message.setWrapStyleWord(true);
        message.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(register); buttons.add(cancel);
        JPanel south = new JPanel(new BorderLayout());
        south.add(message, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.SOUTH);

        add(form, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        register.addActionListener(e -> submit());
        cancel.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(register);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(owner);
    }

    public boolean wasRegistered() { return registered; }

    private void submit() {
        register.setEnabled(false);
        showMessage("Saving...", false);
        final String r = regNo.getText(), n = name.getText(), m = email.getText();
        final EventInfo ev = (EventInfo) event.getSelectedItem();
        new SwingWorker<Void, Void>() {                          // database call off the event thread
            @Override protected Void doInBackground() throws Exception {
                service.register(r, n, m, ev);
                return null;
            }
            @Override protected void done() {
                register.setEnabled(true);
                try {
                    get();
                    registered = true;
                    JOptionPane.showMessageDialog(RegisterDialog.this, "Registration saved.");
                    dispose();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable c = ex.getCause();
                    if (c instanceof ValidationException) showMessage(c.getMessage(), true);
                    else if (c instanceof DuplicateRegistrationException) showMessage(c.getMessage(), true);
                    else if (c instanceof SQLException) showMessage("Database error: " + c.getMessage(), true);
                    else showMessage("Unexpected error: " + c, true);
                }
            }
        }.execute();
    }

    private void showMessage(String text, boolean error) {
        message.setForeground(error ? new Color(170, 0, 0) : new Color(0, 100, 0));
        message.setText(text);
        pack();
    }
}

class MainFrame extends JFrame {
    private static final int PAGE_SIZE = 50;
    private static final String[] COLUMNS = {"Reg No", "Name", "Email", "Event", "Registered At"};

    private final RegistrationService service;
    private final DefaultTableModel model = new DefaultTableModel(COLUMNS, 0) {
        @Override public boolean isCellEditable(int row, int col) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JComboBox<EventInfo> filter = new JComboBox<>();
    private final JButton prev = new JButton("Prev");
    private final JButton next = new JButton("Next");
    private final JLabel pageLabel = new JLabel("Page - of -");
    private final JLabel status = new JLabel("Ready");

    private List<EventInfo> events = new ArrayList<>();
    private int page = 0;
    private int total = 0;
    private int requestId = 0;      // ignores results of out-of-date requests

    public MainFrame(RegistrationService service) {
        super("Campus Event Registrations");
        this.service = service;
        setJMenuBar(buildMenuBar());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Filter by event:"));
        top.add(filter);

        JPanel bottom = new JPanel(new BorderLayout());
        JPanel nav = new JPanel(new FlowLayout(FlowLayout.CENTER));
        nav.add(prev); nav.add(pageLabel); nav.add(next);
        bottom.add(nav, BorderLayout.CENTER);
        status.setBorder(BorderFactory.createEmptyBorder(2, 8, 4, 8));
        bottom.add(status, BorderLayout.SOUTH);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        filter.addActionListener(e -> { page = 0; loadPage(); });
        prev.addActionListener(e -> { if (page > 0) { page--; loadPage(); } });
        next.addActionListener(e -> { if ((page + 1) * PAGE_SIZE < total) { page++; loadPage(); } });

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(820, 480);
        setLocationRelativeTo(null);
        loadEvents();
    }

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");
        JMenuItem export = new JMenuItem("Export CSV...");
        JMenuItem exit = new JMenuItem("Exit");
        export.addActionListener(e -> exportCsv());
        exit.addActionListener(e -> dispose());
        file.add(export); file.addSeparator(); file.add(exit);

        JMenu regs = new JMenu("Registrations");
        JMenuItem add = new JMenuItem("New Registration...");
        JMenuItem refresh = new JMenuItem("Refresh");
        add.setAccelerator(KeyStroke.getKeyStroke("control N"));
        refresh.setAccelerator(KeyStroke.getKeyStroke("F5"));
        add.addActionListener(e -> openRegisterDialog());
        refresh.addActionListener(e -> loadPage());
        regs.add(add); regs.add(refresh);

        JMenu help = new JMenu("Help");
        JMenuItem about = new JMenuItem("About");
        about.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "Campus Event Registration\nCSA09 - Assignment 4 (Task 6)"));
        help.add(about);

        bar.add(file); bar.add(regs); bar.add(help);
        return bar;
    }

    // ---------- background work (SwingWorker keeps the window responsive) ----------

    private void loadEvents() {
        setBusy(true, "Loading events...");
        new SwingWorker<List<EventInfo>, Void>() {
            @Override protected List<EventInfo> doInBackground() throws Exception { return service.events(); }
            @Override protected void done() {
                try {
                    events = get();
                    filter.removeAllItems();
                    filter.addItem(EventInfo.ALL);
                    for (EventInfo ev : events) filter.addItem(ev);   // triggers loadPage via the filter listener
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    fail(ex.getCause());
                }
            }
        }.execute();
    }

    private void loadPage() {
        final EventInfo selected = (EventInfo) filter.getSelectedItem();
        final int pageIndex = page;
        final int id = ++requestId;
        setBusy(true, "Loading...");
        new SwingWorker<Object[], Void>() {
            @Override protected Object[] doInBackground() throws Exception {
                int count = service.count(selected);
                List<Registration> rows = service.page(selected, PAGE_SIZE, pageIndex);
                return new Object[]{count, rows};
            }
            @SuppressWarnings("unchecked")
            @Override protected void done() {
                if (id != requestId) return;                         // a newer request is in flight
                try {
                    Object[] result = get();
                    total = (Integer) result[0];
                    model.setRowCount(0);
                    for (Registration r : (List<Registration>) result[1])
                        model.addRow(new Object[]{r.regNo(), r.name(), r.email(), r.event(), r.registeredAt()});
                    int pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
                    pageLabel.setText("Page " + (page + 1) + " of " + pages);
                    setBusy(false, total + " registration(s)");
                    prev.setEnabled(page > 0);
                    next.setEnabled((page + 1) * PAGE_SIZE < total);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    fail(ex.getCause());
                }
            }
        }.execute();
    }

    private void openRegisterDialog() {
        RegisterDialog dialog = new RegisterDialog(this, service, events);
        dialog.setVisible(true);                                     // modal: returns when closed
        if (dialog.wasRegistered()) loadPage();
    }

    private void exportCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("registrations.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        final File target = chooser.getSelectedFile();
        final EventInfo selected = (EventInfo) filter.getSelectedItem();
        setBusy(true, "Exporting...");
        new SwingWorker<Integer, Void>() {
            @Override protected Integer doInBackground() throws Exception {
                try (BufferedWriter w = Files.newBufferedWriter(target.toPath(), StandardCharsets.UTF_8)) {
                    return service.exportCsv(selected, w);
                }
            }
            @Override protected void done() {
                try {
                    int n = get();
                    setBusy(false, "Exported " + n + " row(s) to " + target.getName());
                    prev.setEnabled(page > 0);
                    next.setEnabled((page + 1) * PAGE_SIZE < total);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    fail(ex.getCause());
