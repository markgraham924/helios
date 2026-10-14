package pl.mateusz.helios;
public final class WeatherLabels {
    private WeatherLabels(){}
    public static String english(String state){
        switch(state){
            case "clear-night":return "Clear night";
            case "cloudy":return "Cloudy";
            case "fog":return "Fog";
            case "hail":return "Hail";
            case "lightning":return "Thunderstorm";
            case "lightning-rainy":return "Thunderstorm with rain";
            case "partlycloudy":return "Partly cloudy";
            case "pouring":return "Heavy rain";
            case "rainy":return "Rain";
            case "snowy":return "Snow";
            case "snowy-rainy":return "Sleet";
            case "sunny":return "Sunny";
            case "windy":case "windy-variant":return "Windy";
            case "exceptional":return "Exceptional conditions";
            default:return "No weather description";
        }
    }
    /** The Material Design icon Home Assistant itself uses for a weather condition; an unknown condition gets none. */
    public static String icon(String state){
        if(state==null)return null;
        switch(state){
            case "clear-night":return "mdi:weather-night";
            case "cloudy":return "mdi:weather-cloudy";
            case "fog":return "mdi:weather-fog";
            case "hail":return "mdi:weather-hail";
            case "lightning":return "mdi:weather-lightning";
            case "lightning-rainy":return "mdi:weather-lightning-rainy";
            case "partlycloudy":return "mdi:weather-partly-cloudy";
            case "pouring":return "mdi:weather-pouring";
            case "rainy":return "mdi:weather-rainy";
            case "snowy":return "mdi:weather-snowy";
            case "snowy-rainy":return "mdi:weather-snowy-rainy";
            case "sunny":return "mdi:weather-sunny";
            case "windy":case "windy-variant":return "mdi:weather-windy";
            case "exceptional":return "mdi:alert-circle-outline";
            default:return null;
        }
    }
}
