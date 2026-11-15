import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

public class LibrarySearch extends Frame {
    private static final String[][] BOOKS = {
        {"Programming in Java", "Computer Science"}, {"Data Structures in C", "Computer Science"},
        {"Operating System Concepts", "Computer Science"}, {"Database System Concepts", "Computer Science"},
        {"Computer Networks", "Computer Science"}, {"Engineering Mathematics", "Mathematics"},
        {"Linear Algebra", "Mathematics"}, {"Discrete Mathematics", "Mathematics"},
        {"Probability and Statistics", "Mathematics"}, {"Engineering Physics", "Physics"},
        {"Quantum Mechanics", "Physics"}, {"Optics", "Physics"}, {"Digital Electronics", "Electronics"},
        {"Signals and Systems", "Electronics"}, {"Microprocessors", "Electronics"}
    };
    private static final int PAGE_SIZE = 5;

    private final TextField query = new TextField(18);
    private final Choice category = new Choice();
    private final Button search = new Button("Search");
    private final Button prev = new Button("Prev");
    private final Button next = new Button("Next");
    private final Button reserve = new Button("Reserve");
    private final java.awt.List resultList = new java.awt.List(8);
    private final Label status = new Label("Enter a title and press Search.");

    private final List<String> matches = new ArrayList<>();
    private int page = 0;

    public LibrarySearch() {
        super("Campus Library Search");
        setLayout(new BorderLayout());                       // whole window

        Label title = new Label("CAMPUS LIBRARY CATALOGUE", Label.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 16));

        category.add("All");
        category.add("Computer Science");
        category.add("Mathematics");
        category.add("Physics");
        category.add("Electronics");

        Panel searchRow = new Panel(new FlowLayout(FlowLayout.LEFT));     // keeps natural size
        searchRow.add(new Label("Title:"));
        searchRow.add(query);
        searchRow.add(category);
        searchRow.add(search);

        Panel centre = new Panel(new BorderLayout());
        centre.add(searchRow, BorderLayout.NORTH);
        centre.add(resultList, BorderLayout.CENTER);                      // the only part that grows

        Panel buttonRow = new Panel(new FlowLayout(FlowLayout.CENTER));
        buttonRow.add(prev);
        buttonRow.add(next);
        buttonRow.add(reserve);

        Panel south = new Panel(new BorderLayout());
        south.add(buttonRow, BorderLayout.CENTER);
        south.add(status, BorderLayout.SOUTH);

        add(title, BorderLayout.NORTH);
        add(centre, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        search.addActionListener(e -> doSearch());
        query.addActionListener(e -> doSearch());
        prev.addActionListener(e -> { if (page > 0) { page--; showPage(); } });
        next.addActionListener(e -> { if ((page + 1) * PAGE_SIZE < matches.size()) { page++; showPage(); } });
        reserve.addActionListener(e -> doReserve());
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { dispose(); }
        });

        setSize(560, 360);
        setMinimumSize(new Dimension(420, 260));
        setLocationRelativeTo(null);
    }

    private void doSearch() {
        String text = query.getText().trim().toLowerCase();
        String cat = category.getSelectedItem();
        matches.clear();
        for (String[] b : BOOKS) {
            boolean titleOk = text.isEmpty() || b[0].toLowerCase().contains(text);
            boolean catOk = cat.equals("All") || b[1].equals(cat);
            if (titleOk && catOk) matches.add(b[0] + "  [" + b[1] + "]");
        }
        page = 0;
        showPage();
    }

    private void showPage() {
        resultList.removeAll();
        int from = page * PAGE_SIZE, to = Math.min(from + PAGE_SIZE, matches.size());
        for (int i = from; i < to; i++) resultList.add(matches.get(i));
        int pages = Math.max(1, (matches.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        status.setText(matches.size() + " result(s) - page " + (page + 1) + " of " + pages);
    }

    private void doReserve() {
        String sel = resultList.getSelectedItem();
        status.setText(sel == null ? "Select a book first." : "Reserved: " + sel);
    }

    public static void main(String[] args) { new LibrarySearch().setVisible(true); }
}
