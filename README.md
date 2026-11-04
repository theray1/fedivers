# FeDivers

FeDivers [1] is a federated SPARQL query engine that prioritizes
graph-pattern-aware logical plans to efficiently execute federated
queries over a federation of SPARQL endpoints. 


## Installation

```sh
git clone 
cd fedivers
mvn clean package
```

## Usage Example

```sh
java -jar fedivers-cli/target/fedivers.jar \
--file queries/q01a.sparql \
--summary summaries/f20_summary.nq \
--type jou \
--strategy fedup \
--endpoint-prefix https://10-54-2-226.gcp.glicid.fr/fedshop200/sparql\?default-graph-uri= \
--no-mdcd-unary-push-down \
--asks
```

## References

[1]  Erwan Boisteau-Desdevises, Gabriela Montoya, Brice Nédelec, Hala Skaf, 
Pascal Molli, et al.. FeDivers: Graph-Pattern-Aware Source Selection for 
Scalable SPARQL Federations. International Semantic Web Conference 2026, 
Oct 2026, Bari (Italy), Italy. ⟨hal-05784639⟩
