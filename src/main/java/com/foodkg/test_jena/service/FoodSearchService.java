package com.foodkg.test_jena.service;

import com.foodkg.test_jena.FoodKGConfig;
import com.foodkg.test_jena.model.Food;
import com.foodkg.test_jena.request.SearchRequest;
import com.foodkg.test_jena.request.SearchResponse;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class FoodSearchService {

    @Autowired
    private SparqlService sparqlService;

    @Autowired
    private LuceneService luceneService;
    @Autowired
    private FoodKGConfig config;

    public SearchResponse searchFoods(SearchRequest request) {
        try {
            List<Food> foods;

            // Si pas de requête textuelle, utiliser SPARQL directement
            if (request.getQuery() == null || request.getQuery().trim().isEmpty()) {
                foods = searchWithSparqlOnly(request);
            } else {
                // Recherche hybride Lucene + SPARQL
                foods = searchWithLuceneAndSparql(request);
            }

            // Enrichir avec les détails complets
            foods = enrichFoodsWithDetails(foods);

            // Pagination
            int totalElements = foods.size();
            int startIndex = request.getPage() * request.getSize();
            int endIndex = Math.min(startIndex + request.getSize(), totalElements);

            List<Food> paginatedFoods = foods.subList(
                    Math.min(startIndex, totalElements),
                    Math.min(endIndex, totalElements));

            return new SearchResponse(paginatedFoods, totalElements, request.getPage(), request.getSize());

        } catch (Exception e) {
            e.printStackTrace();
            return new SearchResponse(List.of(), 0, 0, request.getSize());
        }
    }

    private List<Food> searchWithSparqlOnly(SearchRequest request) {
        // Recherche basée uniquement sur les critères SPARQL
        if (request.getFoodClass() != null && !request.getFoodClass().isEmpty()) {
            return sparqlService.searchByClass(request.getFoodClass());
        }

        if (request.getMinCalories() != null || request.getMaxCalories() != null ||
                request.getMinProtein() != null || request.getMaxProtein() != null) {
            return sparqlService.searchByNutritionalRange(
                    request.getMinCalories(), request.getMaxCalories(),
                    request.getMinProtein(), request.getMaxProtein());
        }

        return sparqlService.getAllFoods();
    }

    private List<Food> searchWithLuceneAndSparql(SearchRequest request) throws Exception {
        // Recherche textuelle avec Lucene
        List<Food> luceneResults = luceneService.searchFoodsAdvanced(
                request.getQuery(),
                request.getFoodClass(),
                request.getFoodGroup(),
                request.getMinCalories(),
                request.getMaxCalories(),
                100 // Récupérer plus de résultats pour filtrage ultérieur
        );

        // Filtrer avec des critères SPARQL additionnels si nécessaire
        if (request.getMinProtein() != null || request.getMaxProtein() != null) {
            luceneResults = luceneResults.stream()
                    .filter(food -> matchesProteinRange(food, request.getMinProtein(), request.getMaxProtein()))
                    .collect(Collectors.toList());
        }

        return luceneResults;
    }

    /**
     * 🎯 Trouve l'URI d'un aliment à partir de son nom
     * Retourne l'URI complet ou null si non trouvé
     */
    public String findFoodUriByName(String searchName) {
        try {
            System.out.println("🔍 Recherche URI pour: " + searchName);

            // 1. Tentative de recherche exacte
            String exactUri = findExactFoodUri(searchName);
            if (exactUri != null) {
                System.out.println("✅ URI exact trouvé: " + exactUri);
                return exactUri;
            }

            // 2. Tentative de recherche approximative
            String approximateUri = findApproximateFoodUri(searchName);
            if (approximateUri != null) {
                System.out.println("📊 URI approximatif trouvé: " + approximateUri);
                return approximateUri;
            }

            // 3. Tentative avec nettoyage du nom
            String cleanedName = cleanSearchName(searchName);
            if (!cleanedName.equals(searchName)) {
                String cleanedUri = findExactFoodUri(cleanedName);
                if (cleanedUri != null) {
                    System.out.println("✨ URI trouvé avec nom nettoyé: " + cleanedUri);
                    return cleanedUri;
                }
            }

            System.out.println("❌ Aucun URI trouvé pour: " + searchName);
            return null;

        } catch (Exception e) {
            System.err.println("❌ Erreur recherche URI: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Recherche exacte d'URI par nom
     */
    private String findExactFoodUri(String searchName) {
        try {
            String sparqlQuery = """
                    PREFIX : <http://example.org/food-ontology#>

                    SELECT DISTINCT ?food WHERE {
                        ?food :name ?name .
                        FILTER(LCASE(STR(?name)) = LCASE("%s"))
                    }
                    LIMIT 1
                    """.formatted(searchName.trim());

            return executeUriQuery(sparqlQuery);

        } catch (Exception e) {
            System.err.println("❌ Erreur recherche exacte URI: " + e.getMessage());
            return null;
        }
    }

    /**
     * Recherche approximative d'URI par nom
     */
    private String findApproximateFoodUri(String searchName) {
        try {
            String sparqlQuery = """
                    PREFIX : <http://example.org/food-ontology#>

                    SELECT DISTINCT ?food WHERE {
                        ?food :name ?name .
                        FILTER(CONTAINS(LCASE(STR(?name)), LCASE("%s")))
                    }
                    ORDER BY STRLEN(?name)
                    LIMIT 1
                    """.formatted(searchName.trim());

            return executeUriQuery(sparqlQuery);

        } catch (Exception e) {
            System.err.println("❌ Erreur recherche approximative URI: " + e.getMessage());
            return null;
        }
    }

    /**
     * Exécute une requête SPARQL et retourne le premier URI trouvé
     */
    /**
     * Exécute une requête SPARQL et retourne le premier URI trouvé
     */
    private String executeUriQuery(String sparqlQuery) {
        try {
            // Utiliser l'endpoint SPARQL comme dans le reste du SparqlService
            try (QueryExecution qexec = QueryExecutionFactory.sparqlService(config.getSparqlEndpoint(), sparqlQuery)) {
                ResultSet results = qexec.execSelect();

                if (results.hasNext()) {
                    QuerySolution soln = results.nextSolution();
                    return soln.getResource("food").getURI();
                }
            }

            return null;

        } catch (Exception e) {
            System.err.println("❌ Erreur exécution requête URI: " + e.getMessage());
            return null;
        }
    }

    /**
     * Nettoie le nom de recherche pour améliorer les chances de match
     */
    private String cleanSearchName(String searchName) {
        return searchName
                .toLowerCase()
                .replace("_", " ")
                .replace("-", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * 🔍 Recherche d'URI avec plusieurs variantes de noms
     */
    public String findFoodUriByNameVariants(List<String> nameVariants) {
        try {
            System.out.println("🔍 Recherche URI avec variantes: " + nameVariants);

            for (String variant : nameVariants) {
                String uri = findFoodUriByName(variant);
                if (uri != null) {
                    System.out.println("✅ URI trouvé avec variante '" + variant + "': " + uri);
                    return uri;
                }
            }

            System.out.println("❌ Aucun URI trouvé pour les variantes: " + nameVariants);
            return null;

        } catch (Exception e) {
            System.err.println("❌ Erreur recherche URI variantes: " + e.getMessage());
            return null;
        }
    }

    private boolean matchesProteinRange(Food food, Double minProtein, Double maxProtein) {
        if (food.getProtein() == null)
            return false;

        if (minProtein != null && food.getProtein() < minProtein)
            return false;
        if (maxProtein != null && food.getProtein() > maxProtein)
            return false;

        return true;
    }

    private List<Food> enrichFoodsWithDetails(List<Food> foods) {
        return foods.stream()
                .map(food -> {
                    try {
                        Food detailedFood = sparqlService.getFoodDetails(food.getUri());
                        if (detailedFood != null) {
                            // Conserver le score de recherche Lucene
                            detailedFood.setSearchScore(food.getSearchScore());
                            return detailedFood;
                        }
                        return food;
                    } catch (Exception e) {
                        return food; // Retourner l'aliment de base en cas d'erreur
                    }
                })
                .collect(Collectors.toList());
    }

    public List<String> getAutocompleteSuggestions(String prefix) {
        try {
            return luceneService.getAutocompleteSuggestions(prefix, 10);
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    public List<String> getFoodClasses() {
        return sparqlService.getFoodClasses();
    }

    public List<String> getFoodGroups() {
        return sparqlService.getFoodGroups();
    }

    public Food getFoodDetails(String foodUri) {
        return sparqlService.getFoodDetails(foodUri);
    }

    public void reindexLucene() {
        try {
            luceneService.indexFoodsFromKnowledgeGraph();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}