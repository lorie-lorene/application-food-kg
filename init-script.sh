#!/bin/bash
set -e

echo "🚀 Initialisation du Food Knowledge Graph..."

# Attendre que Fuseki soit prêt
echo "⏳ Attente de Fuseki..."
until curl -s http://fuseki:3030/$/ping > /dev/null 2>&1; do
    echo "   Fuseki pas encore prêt, attente..."
    sleep 3
done
echo "✅ Fuseki est prêt!"

# Vérifier si le dataset existe déjà
echo "🔍 Vérification du dataset food-kg-v6..."
if curl -s http://fuseki:3030/$/datasets | grep -q "food-kg-v6"; then
    echo "ℹ️  Dataset food-kg-v2 existe déjà"
    
    # Vérifier s'il contient des données
    COUNT=$(curl -s -X POST http://fuseki:3030/food-kg-v2/query \
        -H "Content-Type: application/sparql-query" \
        -d "SELECT (COUNT(*) as ?count) WHERE { ?s ?p ?o }" \
        | grep -o '"[0-9]*"' | tr -d '"' | head -1)
    
    if [ "$COUNT" -gt "0" ]; then
        echo "✅ Dataset contient déjà $COUNT triples, pas besoin de recharger"
        exit 0
    else
        echo "⚠️  Dataset vide, rechargement des données..."
    fi
else
    echo "📊 Création du dataset food-kg-v6..."
    curl -X POST http://fuseki:3030/\$/datasets \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "dbName=food-kg-v2&dbType=tdb2"
    echo "✅ Dataset créé!"
fi

# Charger les données TTL
echo "📂 Chargement des données depuis /staging/food-kg-export.ttl..."
if [ -f "/staging/food-kg-export.ttl" ]; then
    curl -X POST http://fuseki:3030/food-kg-v6/data \
        -H "Content-Type: text/turtle" \
        --data-binary @/staging/food-kg-export.ttl
    echo "✅ Données TTL chargées!"
    
    # Vérification finale
    echo "🔍 Vérification finale..."
    FINAL_COUNT=$(curl -s -X POST http://fuseki:3030/food-kg-v2/query \
        -H "Content-Type: application/sparql-query" \
        -d "SELECT (COUNT(*) as ?count) WHERE { ?s ?p ?o }" \
        | grep -o '"[0-9]*"' | tr -d '"' | head -1)
    
    echo "✅ Dataset food-kg-v2 contient maintenant $FINAL_COUNT triples!"
    
    # Attendre que Spring Boot soit prêt puis réindexer Lucene
    echo "⏳ Attente de Spring Boot pour réindexation Lucene..."
    until curl -s http://test-jena-app:8080/actuator/health > /dev/null 2>&1; do
        echo "   Spring Boot pas encore prêt..."
        sleep 5
    done
    
    echo "🔄 Réindexation Lucene..."
    curl -X POST http://test-jena-app:8080/api/foods/reindex || echo "⚠️  Réindexation échouée, sera faite plus tard"
    
else
    echo "❌ Fichier /staging/food-kg-export.ttl introuvable!"
    exit 1
fi

echo "🎉 Initialisation terminée avec succès!"
