const {test}=require('node:test'),assert=require('node:assert/strict');
test('charts never invent missing observations or report a percentage against an unknown baseline',()=>{
    const {chartModel}=require('../../main/resources/static/js/government-charts.js');
    const target={metric:'x',unit:'homes',targetValue:100,baseline:null,scope:{level:'NATIONAL',code:'MA'},methodologyKey:'v1'};
    assert.equal(chartModel(target,[]).available,false);
    const value={metric:'x',unit:'homes',scope:target.scope,methodologyKey:'v1',value:25,periodStart:'2026-01-01',periodEnd:'2026-09-30',evidenceIds:['proof']};
    const chart=chartModel(target,[value]);assert.equal(chart.available,true);assert.equal(chart.bars.length,2);assert.equal(chart.progress,null);assert.equal(chart.bars[0].value,25);assert.equal(chart.bars[1].value,100);
    assert.equal(chartModel({...target,targetValue:-10},[{...value,value:-20}]).bars.length,0,'negative observations remain a table, not misleading positive bars');
});
test('time series uses actual period spacing rather than equal spacing for irregular observations',()=>{
    const {timeSeries}=require('../../main/resources/static/js/government-charts.js');
    const points=timeSeries([{periodEnd:'2026-01-01',value:10},{periodEnd:'2026-01-02',value:20},{periodEnd:'2026-12-31',value:30}]);
    assert.equal(points.length,3);assert.ok(points[1].x-points[0].x<5);assert.ok(points[2].x-points[1].x>200);
    assert.deepEqual(timeSeries([{periodEnd:'invalid',value:10}]),[]);
});
test('time series supplies visible value and date axes without relying on tooltips',()=>{
    const {seriesLabels}=require('../../main/resources/static/js/government-charts.js');
    const labels=seriesLabels([{periodEnd:'2026-01-01',value:40},{periodEnd:'2026-03-01',value:80}],'en');
    assert.deepEqual(labels.values,['0','40','80']);assert.equal(labels.dates.length,2);assert.match(labels.dates[1],/Mar/);
});
