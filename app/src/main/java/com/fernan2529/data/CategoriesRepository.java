package com.fernan2529.data;



public class CategoriesRepository {
    // Fuente de datos única
    private static final String[] CATEGORIES = {
            "-",
            "Anime",
            "CanalesECU",
            "Comedia",
            "Deportes",
            "Doramas",
            "Entretenimiento",
            "Historia",
            "Hogar",
            "Infantiles",
            "Musica",
            "Noticias",
            "Novelas",
            "Peliculas",
            "Series"




    };



    // Devuelve el arreglo
    public String[] getCategories() {
        return CATEGORIES;
    }
}
