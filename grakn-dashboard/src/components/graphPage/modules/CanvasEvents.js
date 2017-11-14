/*
 * Grakn - A Distributed Semantic Database
 * Copyright (C) 2016  Grakn Labs Limited
 *
 * Grakn is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Grakn is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Grakn. If not, see <http://www.gnu.org/licenses/gpl.txt>.
 */


import EngineClient from '../../../js/EngineClient';
import * as API from '../../../js/util/HALTerms';
import { EventHub } from '../../../js/state/graphPageState';
import CanvasHandler from './CanvasHandler';

let doubleClickTime = 0;


function holdOnNode(param) {
  visualiser.network.unselectAll();
  const node = visualiser.getNodeOnCoordinates(param.pointer.canvas);
  if (node === null) return;
  EventHub.$emit('show-label-panel', visualiser.getAllNodeProperties(node), visualiser.getNodeType(node), node);
}

function doubleClick(param) {
  doubleClickTime = new Date();
  const node = param.nodes[0];
  if (node === undefined) {
    return;
  }

  const eventKeys = param.event.srcEvent;
  const nodeObj = visualiser.getNode(node);

  if (eventKeys.shiftKey) {
    if (nodeObj.baseType !== API.INFERRED_RELATIONSHIP_TYPE) { requestExplore(nodeObj); }
  } else {
    EngineClient.request({
      url: nodeObj.href,
    }).then(resp => CanvasHandler.onGraphResponse(resp, false, false, false, node))
    .then((instances) => { CanvasHandler.loadInstancesAttributes(0, instances); })
    .catch((err) => { EventHub.$emit('error-message', err.message); });
  }
}

function rightClick(param) {
  const node = param.nodes[0];
  if (node === undefined) { return; }

  if (param.event.shiftKey) {
    param.nodes.forEach((x) => { visualiser.deleteNode(x); });
  }
}

function hoverNode(param) {
  EventHub.$emit('hover-node', param);
}

function blurNode() {
  EventHub.$emit('blur-node');
}

function requestExplore(nodeObj) {
  if (nodeObj.explore) {
    EngineClient.request({
      url: nodeObj.explore,
    }).then(resp => CanvasHandler.onGraphResponse(resp, false, true, true, nodeObj.id), (err) => {
      EventHub.$emit('error-message', err.message);
    });
  }
}

function leftClick(param) {
  const node = param.nodes[0];
  const eventKeys = param.event.srcEvent;
  const clickType = param.event.type;

      // If it is a long press on node: return and onHold() method will handle the event.
  if (clickType !== 'tap') {
    return;
  }

      // Check if we need to start or stop drawing the selection rectangle
  visualiser.checkSelectionRectangleStatus(node, eventKeys, param);

  if (node === undefined) {
    return;
  }

  const nodeObj = visualiser.getNode(node);

  if (eventKeys.shiftKey) {
    requestExplore(nodeObj);
  } else {
    EventHub.$emit('show-node-panel', nodeObj);
  }
}

function singleClick(param) {
      // Everytime the user clicks on canvas we clear the context-menu and tooltip
  EventHub.$emit('close-context');
  EventHub.$emit('close-tooltip');

  const t0 = new Date();
  const threshold = 200;
      // all this fun to be able to distinguish a single click from a double click
  if (t0 - doubleClickTime > threshold) {
    setTimeout(() => {
      if (t0 - doubleClickTime > threshold) {
        leftClick(param);
      }
    }, threshold);
  }
}

function onDragStart(params) {
  const eventKeys = params.event.srcEvent;
  visualiser.draggingNode = true;
  EventHub.$emit('close-tooltip');
      // If ctrl key is pressed while dragging node/nodes we also unlock and drag the connected nodes
  if (eventKeys.ctrlKey) {
    const neighbours = [];
    params.nodes.forEach((node) => {
      neighbours.push(...visualiser.network.getConnectedNodes(node));
      neighbours.push(node);
    });
    visualiser.network.selectNodes(neighbours);
    visualiser.releaseNodes(neighbours);
  } else {
    visualiser.releaseNodes(params.nodes);
  }
}


function registerCanvasEvents() {
  visualiser.setCallbackOnEvent('click', param => singleClick(param));
  visualiser.setCallbackOnEvent('doubleClick', param => doubleClick(param));
  visualiser.setCallbackOnEvent('oncontext', param => rightClick(param));
  visualiser.setCallbackOnEvent('hold', param => holdOnNode(param));
  visualiser.setCallbackOnEvent('hoverNode', param => hoverNode(param));
  visualiser.setCallbackOnEvent('blurNode', param => blurNode(param));
  visualiser.setCallbackOnEvent('dragStart', param => onDragStart(param));
}

export default {
  registerCanvasEvents,
};
