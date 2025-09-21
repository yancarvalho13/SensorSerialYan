package org.Yan.view;

import org.Yan.Produto;
import org.Yan.repo.ProdutoRepository;

import javax.swing.table.AbstractTableModel;

public class TableModel extends AbstractTableModel {
    ProdutoRepository repository;
    private final String[] cols = {"Produto", "Código da Tag", "Setor"};
    private java.util.List<Produto> rows = new java.util.ArrayList<>();

    public TableModel(ProdutoRepository repository) {
        this.repository = repository;
        reload();
    }

    public void reload(){
        this.rows = repository.findAll();
        fireTableDataChanged();
    }

    public TableModel() {
    }


    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return cols.length;
    }

    @Override
    public String getColumnName(int column) {
        return cols[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Produto p = rows.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> p.getNome();              // ajuste para seu campo
            case 1 -> p.getEtiqueta().getTag();        // ex.: getCodigoTag() ou getEpc()
            case 2 -> p.getSetor();            // ajuste para seu campo
            default -> "";
        };
    }


}
