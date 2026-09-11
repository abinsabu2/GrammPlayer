<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Itinerary</title>
</head>
<body>
    {%- for participant in participants %}
        {{- participant.name }}{% if not loop.last %} / {% endif -%}
    {%- endfor -%}
    <br>
    {% set items = itemsDependingOnContext(object) %}
    <h3>{{ orderItemService.departureDateByItems(items) | date('d.m.Y') }} - {{ orderItemService.arrivalDateByItems(items) | date('d.m.Y') }}</h3>

    <h3 class="font-weight-bold">{{ journey.itinerary.title }}</h3>
    <br>
    <h3 class="red font-weight-bold">mit&nbsp;
        {%- for ship in ships(items) -%}
            {{ ship.name }}{% if not loop.last %}, {% endif %}
        {%- endfor -%}
    </h3>
</body>
</html>
