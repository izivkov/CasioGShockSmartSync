import os
import glob
import re

descriptions = {
    'values': "<li><b>Sidereal Time:</b> Calculated based on the Earth\\'s rotation relative to the stars. A sidereal day is about 4 minutes shorter than a solar day.</li>",
    'values-ar': "<li><b>التوقيت النجمي:</b> يُحسب بناءً على دوران الأرض بالنسبة للنجوم. اليوم النجمي أقصر بحوالي 4 دقائق من اليوم الشمسي.</li>",
    'values-bg': "<li><b>Звездно време:</b> Изчислява се въз основа на въртенето на Земята спрямо звездите. Звездният ден е с около 4 минути по-кратък от слънчевия.</li>",
    'values-ca': "<li><b>Temps sideral:</b> Calculat basat en la rotació de la Terra respecte a les estrelles. Un dia sideral és aproximadament 4 minuts més curt que un dia solar.</li>",
    'values-de': "<li><b>Sternzeit:</b> Berechnet auf der Grundlage der Erdrotation relativ zu den Sternen. Ein Sterntag ist etwa 4 Minuten kürzer als ein Sonnentag.</li>",
    'values-es': "<li><b>Tiempo sidéreo:</b> Calculado basándose en la rotación de la Tierra en relación con las estrellas. Un día sidéreo es aproximadamente 4 minutos más corto que un día solar.</li>",
    'values-fr': "<li><b>Temps sidéral:</b> Calculé sur la base de la rotation de la Terre par rapport aux étoiles. Un jour sidéral est environ 4 minutes plus court qu\\'un jour solaire.</li>",
    'values-hu': "<li><b>Sziderikus idő:</b> A Föld csillagokhoz viszonyított forgása alapján számítva. Egy sziderikus nap körülbelül 4 perccel rövidebb, mint egy szoláris nap.</li>",
    'values-ja': "<li><b>恒星時:</b> 恒星に対する地球の自転に基づき計算されます。恒星日は太陽日より約4分短くなります。</li>",
    'values-ru': "<li><b>Звездное время:</b> Рассчитывается на основе вращения Земли относительно звезд. Звездные сутки примерно на 4 минуты короче солнечных.</li>",
    'values-zh-rCN': "<li><b>恒星时:</b> 基于地球相对于恒星的自转计算。恒星日比太阳日短约 4 分钟。</li>"
}

base_dir = '/home/izivkov/projects/CasioGShockSmartSync/app/src/main/res'
for lang_dir in descriptions.keys():
    strings_file = os.path.join(base_dir, lang_dir, 'strings.xml')
    if os.path.exists(strings_file):
        with open(strings_file, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # Replace \x01 with CDATA end string ]]>
        content = content.replace('\x01', ']]>')
        
        with open(strings_file, 'w', encoding='utf-8') as f:
            f.write(content)

print("Strings fixed successfully.")
