package org.Yan.repo;

import org.Yan.Produto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {
    List<Produto> findByNome(String nome);

    Produto findDistinctByNome(String nome);
}
