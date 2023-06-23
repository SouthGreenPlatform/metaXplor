let StringBuffer = function() {
    this.buffer = new Array();
};
StringBuffer.prototype.append = function(str) {
    this.buffer[this.buffer.length] = str;
};
StringBuffer.prototype.toString = function() {
    return this.buffer.join("");
};

function getSampleDetailsTable(sampleId, module, queryUrl, projectFieldName, dateType) {
    let tableHTML;
    $.ajax({
        url: queryUrl,
        async: false,
        type: 'get',
        data: {
            "module": module,
            "sample": sampleId
        },
        success: function (jsonResult) {                                   
            let headerPositions = new Array(), tableRow = new Array();
        	for (let key in jsonResult) {
        		if ("_id" != key && projectFieldName != key) {
        				for (let spFieldKey in jsonResult[key])
						{
                    		let headerPos = headerPositions[spFieldKey];
                    		if (headerPos == null)
                    		{
                    			headerPos = Object.keys(headerPositions).length;
                    			headerPositions[spFieldKey] = headerPos;
                    		}
                    		let cellContents = jsonResult[key][spFieldKey];
                    		tableRow[headerPos] = dateType == key ? new Date(cellContents).toISOString().split('T')[0] : cellContents;
    					}
        		}
        	}
        	
            let tableHeader = new Array();
            tableHeader[0] = "Sample";
            for (let header in headerPositions)
            	tableHeader[headerPositions[header] + 1] = !isNaN(header) ? getFieldName(header) : header;
            
            let htmlTableContents = new StringBuffer();
            htmlTableContents.append('<tr>');
            for (let headerPos in tableHeader)
            	htmlTableContents.append('<th>' + tableHeader[headerPos] + '</th>');
            htmlTableContents.append('</tr><tr>');
            htmlTableContents.append('<td>' + jsonResult._id + '</td>');
            for (let i=0; i<tableHeader.length - 1; i++)
            	htmlTableContents.append("<td style='max-width:125px; word-break:normal;'>" + (tableRow[i] != null ? (Array.isArray(tableRow[i]) ? tableRow[i].join(", ") : tableRow[i]) : "") + "</td>");
            htmlTableContents.append('</tr>');

            tableHTML = "<table class='seqDetailTable'>" + htmlTableContents + "</table";
        },
        error: function (xhr, ajaxOptions, thrownError) {
            handleError(xhr);
        }
    });
    return tableHTML;
}

//function to get url param
function $_GET(param) {
    var vars = {};
    window.location.href.replace(location.hash, '').replace(
            /[?&]+([^=&]+)=?([^&]*)?/gi, // regexp
            function (m, key, value) { // callback
                vars[key] = value !== undefined ? value : '';
            }
    );
    if (param) {
        return vars[param] ? vars[param] : null;
    }
    return vars;
}

jQuery.fn.selectText = function(){
    this.find('input').each(function() {
        if($(this).prev().length == 0 || !$(this).prev().hasClass('p_copy')) { 
            $('<p class="p_copy" style="position: absolute; z-index: -1;"></p>').insertBefore($(this));
        }
        $(this).prev().html($(this).val());
    });
    var doc = document;
    var element = this[0];
    console.log(this, element);
    if (doc.body.createTextRange) {
        var range = document.body.createTextRange();
        range.moveToElementText(element);
        range.select();
    } else if (window.getSelection) {
        var selection = window.getSelection();        
        var range = document.createRange();
        range.selectNodeContents(element);
        selection.removeAllRanges();
        selection.addRange(range);
    }
};