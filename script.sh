#!/bin/bash

while true; do
    echo "=============================="
    echo "Compilation du programme Java..."
    javac -cp "lib/*" -d out src/main/java/finalagent/*.java

    if [ $? -ne 0 ]; then
        echo " Échec de la compilation Java. Attente 5s avant nouvelle tentative..."
        sleep 5
        continue
    fi

    # Récupère l'adresse IP locale (hors 127.0.0.1)
    ip_locale=$(ifconfig | grep -w inet | grep -v 127.0.0.1 | awk '{print $2}' | head -n 1)
    dernier_octet=$(echo "$ip_locale" | cut -d '.' -f 4)

    # Recherche des hôtes actifs sur le port 60000
    ip=$(sudo nmap -p 60000 --open -oG - 192.168.224.0/24 | awk '/Up$/{print $2}' | head -n 1)

    echo " Lancement du programme Java avec IP locale $ip_locale (dernier octet: $dernier_octet) et IP distante: $ip"

    # Exécute Java avec ou sans second argument
    if [ -n "$ip" ]; then
        java -cp "out:lib/*" finalagent.Main "$dernier_octet" "$ip"
    else
        java -cp "out:lib/*" finalagent.Main "$dernier_octet"
    fi

    # Vérifie le code de sortie du programme Java
    exit_code=$?
    echo "🔁 Le programme Java s'est arrêté (code $exit_code). Redémarrage dans 5 secondes..."
    sleep 15
done
